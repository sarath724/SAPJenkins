//
// Defaults
//
JENKINS_MAIL = 'DL_56D935FC5F99B7DB990002BB@exchange.sap.corp'  // DL BS Developer
GITHUB_API_URL = 'https://github.wdf.sap.corp/api/v3'
GITHUB_REPO = 'https://github.wdf.sap.corp/bs-automation'
BUILD_DIR = 'build'
LIB_REPOSITORY = 'https://github.wdf.sap.corp/bs-automation/sap-bs-jenkins-scripts'

// load additional methods
gitLib = null
releaseLib = null
fileLoader.withGit(LIB_REPOSITORY, 'master', null, '') {
        gitLib = fileLoader.load('pipelines/lib/git')
        releaseLib = fileLoader.load('pipelines/lib/release')
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

//
// Linting Step - rubocop
//
def rubocop() {
    node(slaveLabel) {
        dir(BUILD_DIR) {
            if("${Type}" == "Pullrequest") {
                gitLib.githubComment("Build ${env.BUILD_URL} started", organization, repository, "${Ref}", GITHUB_API_URL)
            }
            gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
            sh '/usr/bin/rubocop.ruby2.1'
        }
    }
}

//
// Build Step
//
def build() {
    node(slaveLabel) {
        dir(BUILD_DIR) {
            gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
            sh "/opt/chef/embedded/bin/gem build *.gemspec"
        }
    }
}

//
// Test step
//
def test() {
    node(slaveLabel) {
        dir(BUILD_DIR) {
            gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
            sh "/opt/chef/embedded/bin/ruby tests/script.rb -T 3600 -v -t ${Token} -o ${Organization}"
        }
    }
}

//
// Release step
//
def release() {
   releaseLib.sendMailTemplate(JENKINS_MAIL, "${Ref}", "${GITHUB_REPO}", repository, releaseLib.selectMail(), "[Jenkins] New version for ${env.JOB_NAME} must be chosen")

    // choose version and code review
    def versionType = null
    catchError {
        versionType = input message: 'Which version?', parameters: [[$class: 'ChoiceParameterDefinition', choices:  'Fix\nMinor\nMajor', description: 'Please select the version type or abort this build', name: 'versionType']]
    }

    // release
    node(slaveLabel) {
        // abort pull request with message
        if (versionType == null) {
            def reason = input message: 'What\'s the reason ', parameters: [[$class: 'TextParameterDefinition', defaultValue: '', description: 'The pull request will be closed and not merged with this text.', name: 'Reason']]
            if("${Type}" == "Pullrequest") {
                gitLib.githubComment("reason", organization, repository, Ref, GITHUB_API_URL)
                gitLib.githubPullrequestSetStatus(reason, organization, repository, Ref, 'closed', GITHUB_API_URL)
            }
            throw new InterruptedException('aborted by human') // TODO stacktrace should be removed somehow
        }

        dir(BUILD_DIR) {
            // clean directory
            sh "rm -f *.gem ._version ._name"

            // get source
            gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)

            // gem name
            sh "ruby -e \"print Gem::Specification::load(Dir.glob(\'*.gemspec\')[0]).name\" > ._name"
            name = readFile "._name"

            // gem version
            sh "ruby -e \"print Gem::Specification::load('${name}.gemspec').version\" > ._version"
            old_version = readFile "._version"

            // set new version
            new_version = releaseLib.bumpVersion(old_version, versionType)

            // gemspec
            sh "ruby -pi -e \"gsub('${old_version}', '${new_version}')\" ${name}.gemspec"

            // gem file
            def gem = "${name}-${new_version}.gem"

            // merge to master
            if("${Type}" == "Branch" && "${Ref}" != "master") {
                sh 'git checkout master'
                sh "git merge ${Ref}"
            }

            // changelog
            sh "git tag ${new_version}"
            checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '../tools-templates']], submoduleCfg: [], userRemoteConfigs: [[url: "${GITHUB_REPO}/tools-templates"]]]
            sh "../tools-templates/tools/git_print_changelog > CHANGELOG.md"

            // commit
            sh "git config user.name 'bs-jenkins'"
            sh "git config user.email '${JENKINS_MAIL}'"
            sh "git add ${name}.gemspec CHANGELOG.md"
            sh "git commit --author='Jenkins <${JENKINS_MAIL}>' -m 'Updated version and change log' ${name}.gemspec CHANGELOG.md"

            // push and release
            sh 'git config push.default simple'
            sh 'git push --set-upstream origin master && git push --tags'

            // Upload to gem repository
            sh "/opt/chef/embedded/bin/gem build ${name}.gemspec"

            sh "curl -f -u geminabox:trustno1 -F name=test -F file=@${gem} http://gems.mo.sap.corp:8080/geminabox/upload"

            if("${Type}" == "Pullrequest") {
                gitLib.githubComment("Build passed\\n Version ${new_version} released", organization, repository, Ref, GITHUB_API_URL)
            }
        }
    }
}

//
// Stages
//
stage 'Linting'
rubocop()

stage 'Build'
build()

stage 'Test'
test()

stage 'Release'
release()
