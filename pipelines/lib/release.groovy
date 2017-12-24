// bump version
def bumpVersion(version, type) {
    def versions =  version.tokenize('.')

    switch(type) {
        case 'Major':
            versions[0] = versions[0].toInteger() + 1
            versions[1] = 0
            versions[2] = 0
            break
        case 'Minor':
            versions[1] = versions[1].toInteger() + 1
            versions[2] = 0
            break
        case 'Fix':
            versions[2] = versions[2].toInteger() + 1
            break
        default:
            break
    }
    return "${versions[0]}.${versions[1]}.${versions[2]}"
}

// select mail
def selectMail() {
    return """Hello,
all steps are almost done. Please select a version for ${env.JOB_NAME} for the next release.
Choose: ${env.BUILD_URL}input"""
}

// error mail
def errorMail() {
    return """Hello,
Build ${env.JOB_NAME} #${env.BUILD_NUMBER} failed. Please see log for further details.
Log: ${env.BUILD_URL}console"""
}

// release mail
def releaseMail(cookbook = null) {
    def shelf = ""
    if (cookbook) {
        shelf = """
        
Shelf version mismatch detected!
https://shelf.mo.sap.corp/cookbooks/${cookbook}"""
    }
    return """Hello

new version has been released.${shelf}"""
}

// template with footer
def sendMailTemplate(recipient, ref, githubRepo, repository, body, subject) {
    mail body: """${body}
Pullrequest: ${githubRepo}/${repository}/pull/${ref}
Build: ${env.BUILD_URL}
Jenkins: ${env.JENKINS_URL}
best regards
Jenkins""", subject: "${subject}", to: recipient
}
return this;
