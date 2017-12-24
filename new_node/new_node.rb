#!/usr/bin/env ruby
#
# This Script installs a new jenkins slave when the queue wait time is too high or not set.
#
# Version 1.0.0

# synchronize output
$stdout.sync = true
$stderr.sync = true

# load gems
require 'json'
require 'date'
require 'optparse'
require 'open-uri'
require 'openssl'
require 'logger'

# set to DEBUG if needwed
logger = Logger.new(STDOUT)
logger.level = Logger::INFO

options = {}
OptionParser.new do |opts|
  opts.banner = "Usage: #{File.basename($PROGRAM_NAME)} [options]"
  opts.on('-h', '--help', 'Show help') do
    puts opts
    exit
  end
  opts.on('-m', '--master JENKINS_MASTER', 'Specific the jenkins master e.g. http[s]://hostname:port') { |v| options[:master] = v }
  opts.on('-w', '--wait MAX_WAIT_SECONDS', 'Deploy a new node when the given wait time is higher than the average wait time in the queue.') { |v| options[:wait] = v }
  opts.on('-p', '--project MONSOON_PROJECT', 'Name of the monsoon project') { |v| options[:project] = v }
  opts.on('-o', '--organization MONSOON_ORGANIZATION', 'Name of the monsoon organization') { |v| options[:organization] = v }
  opts.on('-s', '--size INSTANCE_SIZE', 'Instance size e.g.') { |v| options[:size] = v }
  opts.on('-t', '--token MONSOON_TOKEN', 'Monsoon token for api authentication') { |v| options[:token] = v }
  opts.on('-e', '--executors SLAVE_EXECUTORS', 'Number of executors for the new node') { |v| options[:executors] = v }
  opts.on('-a', '--availabilityZone AVAILABILITYZONE', 'Availability zone of the new node. e.g. rot_2') { |v| options[:availability_zone] = v }
  opts.on('-x', '--timeout TIMEOUT', 'Provision timeout') { |v| options[:timeout] = v }
  opts.on('-u', '--endpoint ENDPOINT_URL', 'Ec2 Endpoint url. Default is europe e.g.: ec2-europe.api.monsoon.mo.sap.corp') { |v| options[:endpoint] = v }
  opts.on('-U', '--username USERNAME ', 'USERNAME' )  { |v| options[:username] = v } 
  opts.on('-P', '--password PASSWORD ', 'Password' )  { |v| options[:password] = v } 
end.parse!

# mandatory options
unless options.has_key?(:master) && options.has_key?(:project) && options.has_key?(:organization) && options.has_key?(:token)
  puts 'Please make sure you have set -m, -p, -o, and -t'
  exit
end

# defaults
options[:wait] = 0 unless options.has_key?(:wait)
options[:executors] = '4' unless options.has_key?(:executors)
options[:size] = 'small_2_2' unless options.has_key?(:size)
options[:timeout] = 30 unless options.has_key?(:timeout)

MAX_WAIT_SECONDS = options[:wait].to_i
CURRENT_TIME = Time.new
PROVISION_RETRY_TIMEOUT = options[:timeout].to_i

# no proxy needed
Excon.defaults[:proxy] = nil

# get queue from master
logger.debug("Open #{options[:master]}/queue/api/json")
average_wait_time = -1
open("#{options[:master]}/queue/api/json", {ssl_verify_mode: OpenSSL::SSL::VERIFY_NONE}) do |json|
  logger.debug("Response: #{json}")
  queue = JSON.parse(json.read)

  # cancel if queue is empty
  break if queue['items'].empty?

  # calculate wait time in seconds
  seconds_queue = []
  queue['items'].each do |item|
    seconds_queue << CURRENT_TIME.to_i - (item['inQueueSince'] / 1000)
  end

  # calculate average wait time
  average_wait_time = seconds_queue.inject(0.0) { |sum, el| sum + el } / seconds_queue.size
  logger.info("Average time: #{average_wait_time}")
end

# run deployment if queue is too high or not set
if average_wait_time > MAX_WAIT_SECONDS || MAX_WAIT_SECONDS == 0
  logger.info('Maximum queue time reached or no wait time set. Instance will be created now.')

  # get monsoon object
#  monsoon_options = {'platform_token' => options[:token]}
#  monsoon_options['ec2_host'] = options[:endpoint] if options.has_key?(:endpoint) && !options[:endpoint].empty?
#  monsoon = Flurry::Monsoon.new(monsoon_options)
 # logger.debug("Creating instance: #{options[:size]}, Organization: #{options[:organization]}, Project: #{options[:project]}")
 # project = monsoon[options[:organization]][options[:project]]

  # set options for the new instance
  instance_options = {'InstanceType' => options[:size]}
  instance_options['Placement.AvailabilityZone'] = options[:availability_zone] if options.has_key?(:availability_zone) && !options[:availability_zone].empty?

  # create a new instance
  instance = project.create_instance_wait('SLES12-SP1-x86_64', instance_options)
  logger.info("Instance #{instance.id} has been created")
  logger.debug('Change runlist and attributes')
  instance.provision_update({'run_list' => 'recipe[scb-compliance],recipe[sap-bs-jenkins::swarm],recipe[sap-bs-jenkins::tools],recipe[sap-os-update]',
                             'custom_attributes' => '{"jenkins":{"swarm":{"master":"' + options[:master] + '","options":"-disableSslVerification -disableClientsUniqueId -executors ' + options[:executors] + '"}}}'})

  # Start Provisioning and install jenkins slave
  logger.info('Run swarm installation')
  logger.debug("Maximum retries #{PROVISION_RETRY_TIMEOUT}")
  tries = 0
  provisioned = false
  begin
    # Sometimes the provisioning is unavailable after instance deployment. Trying to run provision as soon as it is available.
    tries += 1
    logger.debug("Provision try ##{tries}")
    instance.provision
    provisioned = true
  rescue Exception => msg
    # No provision available. Next try in 60 seconds.
    provisioned = false
    logger.debug(msg)
    logger.info('waiting until automation becomes available') if tries % 10 == 0|| tries == 1
    sleep 60
    retry if PROVISION_RETRY_TIMEOUT > tries
  end

  if provisioned
    logger.info('Provisioning started.')
    logger.info('New node will be available shortly.')
  else
    # delete instance due monsoon provision error
    instance.terminate
    logger.info('Instance terminated')
    logger.error('Provisioning failed')
    exit 1
  end
else
  # queue is empty
  logger.info('No new node needed')
end
