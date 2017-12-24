new_node
========
Create a new jenkins node. Tested on SLES 12 with ruby 2.1.
It creates a new instance, run scb-compliance and swarm recipe.

Requirements
------------
* flurry
* openssl
* json
* recipe[scb-compliance]
* recipe[sap-bs-jenkins::swarm]

Usage/Options
-----
Mandatory
* `-m` - jenkins master (http://..)
* `-p` - monsoon project name
* `-o` - monsoon organization name
* `-s` - monsoon instance size
* `-t` - monsoon api token (user token)

Optional
* `-w` - Time in seconds. Create a new node if the average queue time is higher than the given time.
* `-e` - Number of executors
* `-a` - Specific the monsoon availability zone.


Example
-------
`ruby new_node.rb -p prod -o jenkins_landscape -s small_2_2 -t XXXXXXYYYYYYYYYYZZZZZZZZ -e 10 -m https://mo-abc123.mo.sap.corp`