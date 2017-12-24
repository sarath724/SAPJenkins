//
// Defaults
//
JENKINS_MAIL = 'DL_56D935FC5F99B7DB990002BB@exchange.sap.corp'  // DL BS Developer
GITHUB_API_URL = 'https://github.wdf.sap.corp/api/v3'
GITHUB_REPO = 'https://github.wdf.sap.corp/bs-automation'
BUILD_DIR = 'build'
LIB_REPOSITORY = 'https://github.wdf.sap.corp/bs-automation/sap-bs-jenkins-scripts'
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
// Available build projects
//
def projects = [
    [endpoint: 'https://monsoon.mo.sap.corp', project: 'internet_dmz_phl_zone_4_sybase365', token: TOKEN],
[endpoint: 'https://monsoon.mo.sap.corp', project: 'internet_dmz_zone_4_public_web', token: TOKEN],
[endpoint: 'https://monsoon.mo.sap.corp', project: 'office_lan_zone_2', token: TOKEN],
[endpoint: 'https://zone1.mo.sap.corp', project: 'internal_business_systems_cp_net2_zone1a', token: TOKEN2],
[endpoint: 'https://zone1.mo.sap.corp', project: 'internal_business_systems_zone1b_basic', token: TOKEN2],
[endpoint: 'https://zone1.mo.sap.corp', project: 'zone1a_internal_business_systems', token: TOKEN2]
]

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
                    sh "/usr/bin/foodcritic.ruby2.1 -c ${chefVersion} -f any ."
                }
            }
        }
    }
}

//
// Deploy Step
//
def deploy(name) {
    return {
        node(slaveLabel) {
            dir(BUILD_DIR) {
                gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                // prepare attributes.json for test-suite
                sh "ruby -e \"require 'json';f = 'tests/${name}/attributes.json';j = File.exist?(f) ? JSON.load(File.read(f)) : JSON.load('{}');j = JSON.load('{}') if j.nil? || j.empty?;File.open(f,'w') { |file|j.merge!('sap-bs-testsuite' => { 'cookbook' => '${repository}', 'test_name' => '${name}' });file.write(j.to_json) }\""
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '../deployscript']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.wdf.sap.corp/bs-automation/bs-deploy']]])

                // set endpoint
                def config = readFile("tests/${name}/config.yml")
                def token = TOKEN
                if(config ==~ /(?s).*zone1\.mo\.sap\.corp.*/) {
                    token = TOKEN2
                }

                // deployment
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    sh "ruby ../deployscript/deploy_script.rb -t ${token} -o ${ORGANISATION} -T 3600 -m ${TAG} -d false -v -x -f tests/${name}/config.yml -j tests/${name}/attributes.json"
                }
            }
        }
    }
}

//
// Build Step
//
def build(endpoint, project, token) {
    return {
        sleep(new Random().nextInt(120)) // testing due monsoon overload
        node(slaveLabel) {
            dir(BUILD_DIR) {
                gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '../deployscript']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.wdf.sap.corp/bs-automation/bs-deploy']]])
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    sh "ruby ../deployscript/deploy_script.rb -t ${token} -A ${endpoint} -o ${ORGANISATION} -m ${TAG} -T 3600 -v -a -p ${project} -c tests/Cheffile -L ${LOCK_URL} -E \"\\\"cookbook '${repository}', :git => 'https://github.wdf.sap.corp/${organization}/${repository}', :ref => '${git_ref}'\\\",\\\"cookbook 'sap-bs-testsuite', :git => 'https://github.wdf.sap.corp/bs-automation/sap-bs-testsuite', :ref => 'master'\\\"\""
                }
            }
        }
    }
}

//
// Test step
//
def test(endpoint, project, token) {
    return {
        node(slaveLabel) {
            dir(BUILD_DIR) {
                gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                    sh "ruby ../deployscript/deploy_script.rb -t ${token} -A ${endpoint} -o ${ORGANISATION} -m ${TAG} -T 3600 -v -a -p ${project} -e 'recipe[sap-bs-testsuite]' -L ${LOCK_URL} -c tests/Cheffile -E \"\\\"cookbook '${repository}', :git => 'https://github.wdf.sap.corp/${organization}/${repository}', :ref => '${git_ref}'\\\",\\\"cookbook 'sap-bs-testsuite', :git => 'https://github.wdf.sap.corp/bs-automation/sap-bs-testsuite', :ref => 'master'\\\"\""
                }
            }
        }
    }
}


// Terminate instances
def terminate(endpoint, project, token) {
    return {
        node(slaveLabel) {
            dir(BUILD_DIR) {
                if(Terminate == 'true') {
                    gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
                    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '../deployscript']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.wdf.sap.corp/bs-automation/bs-deploy']]])
                    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
                        sh "ruby ../deployscript/deploy_script.rb -t ${token} -A ${endpoint} -o ${ORGANISATION} -m ${TAG} -v -a -p ${project} -d"
                    }
                }
            }
        }
    }
}

//
// Release step
//
def release() {
    // send mail to developer before release
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
                gitLib.githubComment(reason, organization, repository, Ref, GITHUB_API_URL)
                gitLib.githubPullrequestSetStatus(reason, organization, repository, Ref, 'closed', GITHUB_API_URL)
            }
            throw new InterruptedException('aborted by human') // TODO stacktrace should be removed somehow
        }

        dir(BUILD_DIR) {
            // clean directory
            sh 'find . -delete'

            // get source
            gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)

            sh "/opt/chef/embedded/bin/ruby -e \"require 'chef/cookbook/metadata'; m = Chef::Cookbook::Metadata.new; m.from_file('metadata.rb'); print m.version\" > ._version"
            old_version = readFile "._version"

            // set new version
            new_version = releaseLib.bumpVersion(old_version, versionType)
            sh "ruby -pi -e \"gsub('${old_version}', '${new_version}')\" metadata.rb"

            // merge to master
            if("${Type}" == "Branch" && "${Ref}" != "master") {
                sh 'git checkout master'
                sh "git merge ${Ref}"
            }
            // new tag
            sh "git tag ${new_version}"
            // changelog
            checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '../tools-templates']], submoduleCfg: [], userRemoteConfigs: [[url: "${GITHUB_REPO}/tools-templates"]]]
            sh "../tools-templates/tools/git_print_changelog > CHANGELOG.md"

            // commit
            sh "git config user.name 'bs-jenkins'"
            sh "git config user.email '${JENKINS_MAIL}'"
            sh "git add metadata.rb CHANGELOG.md"
            sh "git commit --author='Jenkins <${JENKINS_MAIL}>' -m 'Updated version and change log' metadata.rb CHANGELOG.md"

            // move tag, push and release
            sh "git tag -f ${new_version}"
            sh 'git config push.default simple'
            sh 'git push --set-upstream origin master && git push --tags'

            if("${Type}" == "Pullrequest") {
                gitLib.githubComment("Build passed\\n Version ${new_version} released", organization, repository, Ref, GITHUB_API_URL)
            }

            // release mail
            releaseLib.sendMailTemplate(JENKINS_MAIL, "${Ref}", "${GITHUB_REPO}", repository, releaseLib.releaseMail(), "[Jenkins] Version ${new_version} of ${repository} has been released")
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

// Deploy
def deploySteps = [:]
def builds = null
node(slaveLabel) {
    dir(BUILD_DIR) {
        gitLib.getGit(gitSsh, "${Type}", "${Ref}", JENKINS_MAIL)
        def deploys = chefLib.getBuilds()
        println "Deployments detected: ${deploys}"
        def deploys_used = []
        for (int i = 0; i < deploys.size; i++) {
            def skip_os = 1
            try {
            	if (exclude_os.contains('/')) {
                	skip_os = sh "ruby -e \"require 'yaml';yaml = YAML.load_file('tests/${deploys[i]}/config.yml');exit(1) unless yaml.include?('os');yaml['os'] =~ ${exclude_os} ? exit(0) : exit(1)\""
            	}
            } catch(e) {
                println e.getMessage()
            }

            def skip_region = 1
            try {
                if (exclude_region.contains('/')) {
                    skip_region = sh "ruby -e \"require 'yaml';yaml = YAML.load_file('tests/${deploys[i]}/config.yml');exit(1) unless yaml.include?('region');yaml['region'] =~ ${exclude_region} ? exit(0) : exit(1)\""
                }
            } catch(e) {
                println e.getMessage()
            }

            // skip deployment if regex matched
 	        if(skip_os == null || skip_region == null) {
               continue
            }
            
            deploySteps[i] = deploy(deploys[i])
            deploys_used << deploys[i]
        }
        println "Deployments used: ${deploys_used}"
    }
}

// Build and Test
def buildSteps = [:]
def testSteps = [:]
def terminateTask = [:]
for (int i = 0; i < projects.size; i++) {
    buildSteps[i] = build(projects[i].endpoint, projects[i].project, projects[i].token)
    testSteps[i] = test(projects[i].endpoint, projects[i].project, projects[i].token)
    terminateTask[i] = terminate(projects[i].endpoint, projects[i].project, projects[i].token)
}

//
// Stages
//
try {
    stage 'Linting'
    parallel lintingSteps

    stage 'Deploy'
    parallel deploySteps

    stage 'Build'
    parallel buildSteps

    stage 'Test'
    parallel testSteps

    stage 'Release'
    release()

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
    parallel terminateTask
}
