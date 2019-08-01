@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='eu.de-swaef.pdf', module='Markdown2Pdf', version='2.0.1' )

import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.JSON


import com.qkyrie.markdown2pdf.*
import com.qkyrie.markdown2pdf.internal.writing.*

import groovyx.net.http.HTTPBuilder
import groovy.cli.commons.CliBuilder

def cli = new CliBuilder(usage:'teamreport [options]', header: 'Options:')
cli.o(type: String, 'org name')
cli.t(type: String, 'team name')
cli.a(type: String, 'auth token (optionally set as env var)')
cli.s(type: String, 'since date (defaults to 7 days ago)')

def options = cli.parse(args)
if (!options.o || !options.t) {
	cli.usage()
	return
}

reporter = new TeamReporter(options.o, options.t, options.s, options.a)
reporter.runReport()

class TeamReporter {

	HTTPBuilder http;
	StringBuilder sb;
	String authToken;
	String teamName;
	String orgName
	String since;
	String reportName;
	File pdfFile;
	def projectMap = [:]

	TeamReporter(orgName, teamName, since, authToken) {
		this.sb = new StringBuilder()
		this.authToken = buildAuthToken(authToken)
		this.teamName = teamName
		this.orgName = orgName
		this.since = buildSinceDate(since)
		this.reportName = "${this.teamName} Commit Report ${now()}"
		this.pdfFile = buildFile()
		this.http = buildHttp()
	}

	def runReport() {
		queryGithubData()
		printReport()
		writePDF()
	}

	def now() {
		Date date = new Date()
		return date.format( 'yyyy-MM-dd HH:mm:ss' )
	}

	def writePDF() {
		Markdown2PdfConverter.newConverter().readFrom { -> this.sb.toString()}.writeTo(new SimpleFileMarkdown2PdfWriter(this.pdfFile)).doIt()
	}

	def printReport() {
		def emptyRepos = []
		write "# ${reportName}"
		write "Listing all commits since ${since}"
		write()
		projectMap.each { k,v ->
			if (v.size == 0) {
				emptyRepos << k
			}
			else {
				write "## ${k}"
				v.each {
					write "* ${it}"
				}
			}
			write()
		}
		write()
		write "## Repos With No Commits Since ${since}"
		emptyRepos.each {
			write "* ${it}"
		}
	}

	def write(line) {
		println(line)
		sb.append(line)
		sb.append("\n")
	}
	def write() {
		println()
		sb.append("\n")
	}

	def queryGithubData() {
		http.request( GET, JSON ) {
			uri.path = "/orgs/${orgName}/teams/${teamName}"
			response.success = { resp, json ->
				findRepos(json.id)
			}
		}
	}

	def findRepos(teamId) {
		http.request( GET, JSON ) {
			uri.path = "/teams/${teamId}/repos"
			uri.query = [per_page: 100]
			response.success = { resp, json ->
				json.each {
					findCommits("${it.name}")
				}
			}
		}
	}

	def findCommits(repoName) {
		def commitList = []
		http.request( GET, JSON ) {
			uri.path = "/repos/${orgName}/${repoName}/commits"
			uri.query = [ since: since]
			response.success = { resp, json ->
				json.each {
					commitList << "${it.commit.message} [${it.commit.committer.name}]"
				}
			}
		}
		projectMap.put(repoName, commitList)
	}

	def buildHttp() {
		HTTPBuilder http = new HTTPBuilder('https://api.github.com')
		http.headers['Authorization'] = "token ${authToken}"
		http.headers['User-Agent'] = "com.damm.user-agent/1.0"
		http.headers['Accept'] = "application/vnd.github.v3+json"
		return http
	}

	def buildSinceDate(since) {
		if (!since) {
			Date date = new Date().plus(-7)
			return date.format( 'yyyy-MM-dd' )
		}
		else {
			return since
		}
	}

	def buildFile() {
		def folder = new File("team")
		if (!folder.exists()) {
			folder.mkdirs()
		}
		return new File(folder, "${reportName}.pdf")
	}
	
	def buildAuthToken(authToken) {
		if (!authToken) {
			return System.getenv()['GITHUB_AUTH_TOKEN']
		}
		return authToken
	}
}

