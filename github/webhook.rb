#!/usr/bin/env ruby
require 'net/http'
require 'nokogiri'
require 'json'
require 'cgi'
require 'find'

cgi = CGI.new
puts cgi.header

# get json
json = JSON.parse(cgi.params['payload'].first)

pull_request_number = nil
pull_request_url = nil

# trigger on new pull request or issue with proper text
if json.key?('issue')
  exit unless json['comment']['body'] == 'build' # trigger build on a specific comment
  pull_request_number = json['issue']['number']
  pull_request_url = json['repository']['html_url']
else
  exit if json['action'] == 'closed' # don't trigger when pullrequst was closed
  pull_request_number = json['number']
  pull_request_url = json['pull_request']['base']['repo']['html_url']
end
# search job
path = '/var/lib/jenkins/jobs'
Find.find(path) do |job_config|
  if job_config =~ /.*config\.xml$/
    job_name = File.basename(File.expand_path('..', job_config))
    doc = Nokogiri::XML(File.open(job_config))
    url = doc.xpath('/*/properties/com.coravy.hudson.plugins.github.GithubProjectProperty/projectUrl').text
    relative_path = 'job' + job_config.sub(path,'').gsub('/config.xml','').gsub('jobs','job')
    next unless "#{pull_request_url}/" == url
    # trigger job with parameters
    `curl -H 'SSL_CLIENT_S_DN: github' -X POST 'http://localhost:8181/#{relative_path}/buildWithParameters?Ref=#{pull_request_number}&Type=Pullrequest'`
    break
  end
end
