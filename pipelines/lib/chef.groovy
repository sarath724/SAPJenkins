def getChefVersion() {
    sh "curl -s https://github.wdf.sap.corp/raw/monsoon/monsoon-base-chef/master/CHEF_VERSION |tr -d '\n' > ._CHEF_VERSION"
    def version = readFile '._CHEF_VERSION'
    sh 'rm -f ._CHEF_VERSION'
    return version
}

def getBuilds() {
    sh "find tests/ -maxdepth 1 ! -path tests/ ! -path tests/default -type d -printf '%f, ' | tr -d '\n' > ._BUILDS"
    def builds = readFile '._BUILDS'
    sh 'rm -f ._BUILDS'
    return builds.replaceAll("\\s","").tokenize(',')
}

return this;