#!/usr/bin/env ruby

#
# CGI script
#
# Mutex mechanism for HTTP.
#
# Request (GET or HEAD) example:
# http://host:port/?id=client1&type=check&name=hana
#
# Response http code depends on the status of the file.
# Body is always empty.
#
require 'cgi'

cgi = CGI.new

header = {}

# client identification
id = cgi.params['id'].first if cgi.params.key?('id')

# operation mode: check or unlock
type = cgi.params.key?('type') ? cgi.params['type'].first : 'check'

# shared file
name = cgi.params.key?('name') ? cgi.params['name'].first : 'lockfile'

# read file and validate request
# HTTP status code:
# 201 - file created and filled with the requested id
# 200 - file truncated and unlocked
# 423 - resource locked due to id is not identical to the request id
File.open(name, 'a+') do |file|
  content = file.read.strip
  owner = content.empty? || id == content
  header['status'] = if owner && type == 'check'
    # create a new lock if possible
    file.truncate(0)
    file.write(id)
    201
  elsif owner && type == 'unlock'
    # unlock
    file.truncate(0)
    200
  else
    # file locked for this request
    423
  end
end

# response
puts cgi.header(header)
