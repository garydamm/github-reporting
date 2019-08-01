# github-reporting
Scripts to build PDF reports that are not available in the UI

## Team Report
Find all `repositories` owned by a `team` and retrieve the commits to master for each repository from the `since` date. Write the results to PDF in the `team` directory.

### Prerequisites
* Groovy
* GitHub authentication token (add env var `GITHUB_AUTH_TOKEN`)

### Run
```groovy teamreport -o {github-org} -t {github-team}```
