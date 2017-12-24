/*
 Get github repository url from project config file.
 */
def getGithubRepo() {
    def url = null
    def job = hudson.model.Hudson.instance.getItemByFullName(env.JOB_NAME)
    Iterator it = job.properties.entrySet().iterator()
    while (it.hasNext()) {
        Map.Entry pair = (Map.Entry)it.next();
        def val = pair.getValue()
        if (val instanceof com.coravy.hudson.plugins.github.GithubProjectProperty) {
            url =  val.getProjectUrlStr()
        }
        it.remove(); // avoids a ConcurrentModificationException
    }
    return url
}

//Generate ssh link from http github
def getGitLink(link) {
    def git = link.replaceAll('https://','git@')
    git = git.replaceFirst('/',':')
    return git.subSequence(0, git.length() - 1) + '.git'
}

// Checkout branch or pullrequest
def getGit(url, type, ref, jenkinsMail) {
    type = type.toLowerCase()
    sh 'find . -delete'
    if(type == 'branch') {
        git branch: ref, url: url
    } else {
        git url
        sh 'git config user.name "bs-jenkins"'
        sh "git config user.email \"${JENKINS_MAIL}\"" // DL BS Jenkins
        sh "git pull --no-edit origin pull/${ref}/head"
    }
}

// comment github issues
def githubComment(comment, org, repo, pr, githubApiUrl) {
    sh "curl -k --silent -n -X POST -d '{\"body\": \"${comment}\"}' ${githubApiUrl}/repos/${org}/${repo}/issues/${pr}/comments"
}

// set pullrequest status
def githubPullrequestSetStatus(comment, org, repo, pr, status, githubApiUrl) {
    sh "curl -k --silent -n -X PATCH -d '{\"state\": \"${status}\"}' ${githubApiUrl}/repos/${org}/${repo}/issues/${pr}"
}

return this;
