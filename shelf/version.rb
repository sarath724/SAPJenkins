#!/usr/bin/env ruby
#
# Check the given cookbook version against the shelf version
# exit with code 0 if both versions are equal otherwise 1
#

require 'json'
require 'open-uri'
require 'optparse'
require 'logger'

logger = Logger.new(STDOUT)
logger.level = Logger::WARN

shelf_api = 'http://shelf.mo.sap.corp:8080/api/v1'
cookbook = ''
version = ''

# parse options
opt = OptionParser.new do |parser|
  parser.on('-c', '--cookbook COOKBOOK', 'Cookbook name of which exist in shelf.') { |v| cookbook = v }
  parser.on('-v', '--version VERSION', 'Version which needs to be checked.') { |v| version = v }
  parser.on('-s', '--shelf SHELF_API', "Shelf api endpoint. Default: #{shelf_api}") { |v| shelf_api = v }
  parser.on('-l', '--level DEBUG_LEVEL', 'Set debug level. Default: WARN') { |v| logger.level = Object.const_get("Logger::#{v}") }
  parser.on('-h', '--help', 'show help') do
    puts parser
    exit
  end
end
opt.parse!

logger.debug "cookbook option: '#{cookbook}'"
logger.debug "version option: '#{version}'"
logger.debug "shelf option: '#{shelf_api}'"

# abort if an option is missing
if cookbook.empty? || version.empty?
  puts opt.help
  exit
end

# load current version from shelf
begin
  url = "#{shelf_api}/cookbooks/#{cookbook}/versions/latest"
  logger.debug "Http request: '#{url}'"
  response = open(url).read
  logger.debug "Http repsonse: '#{response}'"
  shelf_version = JSON.parse(response)['version']
rescue OpenURI::HTTPError => e
  logger.error e.message
  if e.message.include?('500')
    logger.debug 'Cookbook could not be found'
    exit 0
  end
end

# compare version
if Gem::Version.new(shelf_version) == Gem::Version.new(version)
  logger.info 'version is equal'
  exit 0
else
  logger.info 'version mismatch'
  logger.debug "Shelf version: '#{shelf_version}'"
  logger.debug "Local version: '#{version}'"
  exit 1
end
