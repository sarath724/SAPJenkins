// install or update the given gem
def installGem(name) {
    def command = 'sudo gem install flurry'
    def code = 0
    // prevent parallel execution
    catchError {
        code = sh "pgrep -f '${command}'"
    }
    println code
    if(code == 0) {
        sh "${command}"
    }
}

// install or update all given gems
def installGems(names) {
    for (int i = 0; i < gems.size; i++) {
        installGem(names[i])
    }
}

return this;