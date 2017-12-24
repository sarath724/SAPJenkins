//
// Defaults
//
JENKINS_MAIL = 'jayesh.jadhav@sap.com'  // DL BS Developer
GITHUB_API_URL = 'https://github.wdf.sap.corp/api/v3'
GITHUB_REPO = 'https://github.wdf.sap.corp/C5266064'
BUILD_DIR = 'build'
LIB_REPOSITORY = 'https://github.wdf.sap.corp/C5266064/sap-bs-jenkins-scripts'
TAG = "${env.JOB_NAME.replaceAll('/','-')}-${env.BUILD_NUMBER}"
ORGANISATION = 'sap_it_bs_automation_build'
LOCK_URL = env.JENKINS_URL.replaceAll('https', 'http')[0..-2] + ":9000"

// Load additional code from external git
gitLib = null
releaseLib = null
chefLib = null
toolsLib = null
fileLoader.withGit(LIB_REPOSITORY, 'master', null, '') {
    gitLib = fileLoader.load('pipelines/lib/git')
    releaseLib = fileLoader.load('pipelines/lib/release')
    chefLib = fileLoader.load('pipelines/lib/chef')
}

//
// Global Variables
//
new_version = null
old_version = null
name = null
gitSsh = gitLib.getGitLink(gitLib.getGithubRepo())
slaveLabel = 'swarm'
organization = gitSsh.tokenize(':')[1].tokenize('/')[0]
repository = gitSsh.tokenize('/')[1].tokenize('.')[0]
buildTag = env.BUILD_TAG

// generate ref
git_ref = 'master'
if("${Type.toLowerCase()}" == 'branch') {
    git_ref = "${Ref}"
} else {
    git_ref = "pr/${Ref}"
}
            

//
// Linting Step - rubocop
//
def rubocop() {
    return {
        node(slaveLabel) {
            dir(BUILD_DIR) {
                if("${Type}" == "Pullrequest") {
                    gitLib.githubComment("Build ${env.BUILD_URL} started", organization, repository, "${Ref}", GITHUB_API_URL)
                }
                gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    sh '/usr/bin/rubocop.ruby2.1'
                }
            }
        }
    }
}

//
// Linting Step - foodcritic
//
def foodcritic() {
    return {
        node(slaveLabel) {
            dir(BUILD_DIR) {
                def chefVersion = chefLib.getChefVersion()
                gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    sh "/usr/bin/foodcritic -c ${chefVersion} -f any ."
                }
            }
        }
    }
}


//
// Configure Steps
//

// Linting
def lintingSteps = [:]
lintingSteps['rubocop'] = rubocop()
lintingSteps['foodcritic'] = foodcritic()

//
// Stages
//
try {
    stage ('Linting'){
    parallel lintingSteps
    }
    /*
    stage 'Deploy'
    parallel deploySteps

    stage 'Build'
    parallel buildSteps

    stage 'Test'
    parallel testSteps

    stage 'Release'
    release()   */

} catch(InterruptedException err) {
    // Manuelly interrupted
    throw(err)
} catch(err) {
    // unexpected interrupted
    node(slaveLabel) {
        // send error mail
        releaseLib.sendMailTemplate(JENKINS_MAIL, "${Ref}", "${GITHUB_REPO}", repository, releaseLib.errorMail(), "[Jenkins] Build ${env.JOB_NAME} #${env.BUILD_NUMBER} failed")

        // comment pull request when an error occurred
        if("${Type}" == "Pullrequest") {
            gitLib.githubComment("Build #${env.BUILD_NUMBER} `failed`\\nDetails: ${env.BUILD_URL}console", organization, repository, Ref, GITHUB_API_URL)
        }
    }
    throw(err)
} finally {
    println("Sorry exception raised")
}
