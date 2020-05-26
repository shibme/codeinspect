package me.shib.security.codefend;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class GitRepo {

    private transient String gitRepoSlug;
    private transient String gitRepoWebURL;
    private transient String gitRepoHttpUri;
    private transient String gitRepoSshUri;
    private transient String gitRepoBranch;
    private transient String gitRepoCommitHash;

    public GitRepo(String gitUri, String gitRepoBranch, String gitRepoCommitHash) {
        init(gitUri, gitRepoBranch, gitRepoCommitHash);
    }

    GitRepo() throws CodeFendException {
        File gitDir = new File(".git");
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            throw new CodeFendException("Not a Git Repository");
        }
        String gitUri = getGitUrlFromLocalRepo();
        if (gitUri == null) {
            throw new CodeFendException("Not a Git Repository");
        }
        String gitBranch = getGitBranchFromLocalRepo();
        String gitCommit = getGitCommitFromLocalRepo();
        init(gitUri, gitBranch, gitCommit);
    }

    private static String runGitCommand(String gitCommand) throws CodeFendException {
        CommandRunner runner = new CommandRunner(gitCommand, "Git");
        runner.suppressConsoleLog();
        try {
            runner.execute();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        String response = runner.getResult();
        if (response.contains("command not found") || response.contains("is currently not installed")) {
            throw new CodeFendException("Git was not found in local environment before proceeding");
        }
        return response;
    }

    private static String getGitUrlFromLocalRepo() throws CodeFendException {
        String response = runGitCommand("git config --get remote.origin.url");
        if (response != null) {
            return response.trim();
        }
        return null;
    }

    private static String getGitBranchFromLocalRepo() throws CodeFendException {
        String response = runGitCommand("git branch");
        try {
            if (response != null) {
                return response.split("\\s+")[1];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getGitCommitFromLocalRepo() throws CodeFendException {
        String commit = runGitCommand("git show --format=%H --no-patch");
        if (commit == null || commit.isEmpty()) {
            return null;
        }
        return commit.trim();
    }

    private static GitRepo getFromLocal() {
        try {
            return new GitRepo();
        } catch (CodeFendException e) {
            return null;
        }
    }

    private void init(String gitUri, String gitRepoBranch, String gitRepoCommitHash) {
        String url = cleanRepoUrl(gitUri);
        String[] urlSplit = url.split("/");
        String host = urlSplit[0];
        String repoName = urlSplit[urlSplit.length - 1];
        String owner = url.replaceFirst(host + "/", "");
        owner = removeEndingSequence(owner, "/" + repoName);
        this.gitRepoSlug = owner + "/" + repoName;
        this.gitRepoWebURL = getWebURL(host, owner, repoName);
        this.gitRepoHttpUri = getHttpUri(host, owner, repoName);
        this.gitRepoSshUri = getSSHUri(host, owner, repoName);
        this.gitRepoBranch = gitRepoBranch;
        this.gitRepoCommitHash = gitRepoCommitHash;
    }

    private String getWebURL(String host, String owner, String repoName) {
        return "https://" + host + "/" + owner + "/" + repoName;
    }

    private String getHttpUri(String host, String owner, String repoName) {
        return "https://" + host + "/" + owner + "/" + repoName + ".git";
    }

    private String getSSHUri(String host, String owner, String repoName) {
        return "git@" + host + ":" + owner + "/" + repoName + ".git";
    }

    private String removeEndingSequence(String source, String seq) {
        if (source.endsWith(seq)) {
            int start = source.lastIndexOf(seq);
            return source.substring(0, start) +
                    source.substring(start + seq.length());
        }
        return source;
    }

    private String cleanRepoUrl(String url) {
        if (url.contains("//")) {
            String[] split = url.split("//");
            url = split[split.length - 1];
        }
        if (url.contains("@")) {
            String[] split = url.split("@");
            url = split[split.length - 1];
        }
        url = removeEndingSequence(url, ".git");
        url = removeEndingSequence(url, "/");
        return url.replaceFirst(":", "/");
    }

    public String getGitRepoSlug() {
        return gitRepoSlug;
    }

    synchronized void cloneRepo(GitCredential credential) throws CodeFendException {
        File currentDir = new File(System.getProperty("user.dir"));
        if (currentDir.list() != null) {
            if (Objects.requireNonNull(currentDir.list()).length > 0) {
                throw new CodeFendException("Not an empty directory");
            }
        }
        if (getFromLocal() != null) {
            throw new CodeFendException("A repository already exists");
        }
        StringBuilder cloneCommand = new StringBuilder();
        cloneCommand.append("git clone ");
        if (this.gitRepoBranch != null && !this.gitRepoBranch.isEmpty()) {
            cloneCommand.append("--branch ").append(this.gitRepoBranch).append(" ");
        }
        cloneCommand.append("--depth 1 ");
        String cloneUri;
        if (credential != null) {
            if (credential.getSshPrivateKeyFile() != null) {
                cloneUri = this.gitRepoSshUri;
                File sshPrivateKeyFile = new File(System.getProperty("user.home") + File.separator +
                        ".ssh" + File.separator + "id_rsa");
                try {
                    Files.copy(credential.getSshPrivateKeyFile().toPath(), sshPrivateKeyFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new CodeFendException(e);
                }
                cloneCommand.append(this.gitRepoSshUri);
            } else {
                cloneUri = this.gitRepoHttpUri;
                String[] splitUrl = this.gitRepoHttpUri.split("//");
                cloneCommand.append(splitUrl[0]).append("//").append(credential.getGitUsername())
                        .append(":").append(credential.getGitAccessToken()).append("@").append(splitUrl[1]);
            }
        } else {
            cloneUri = this.gitRepoHttpUri;
            cloneCommand.append(this.gitRepoHttpUri);
        }
        cloneCommand.append(" .");
        System.out.println("Cloning Repository: " + cloneUri);
        runGitCommand(cloneCommand.toString());
        GitRepo localRepo = getFromLocal();
        if (localRepo == null) {
            throw new CodeFendException("Failed to clone the repo");
        }
        if (this.gitRepoBranch != null && !this.gitRepoBranch.isEmpty()) {
            GitRepo local = GitRepo.getFromLocal();
            if (local == null || !local.gitRepoBranch.equalsIgnoreCase(this.gitRepoBranch)) {
                throw new CodeFendException("Something went wrong. Please validate the branch name");
            }
        }
        if (this.gitRepoCommitHash != null && !this.gitRepoCommitHash.isEmpty()) {
            System.out.println("Checking out commit: " + gitRepoCommitHash);
            runGitCommand("git checkout " + this.gitRepoCommitHash);
            GitRepo local = GitRepo.getFromLocal();
            if (local == null || !local.gitRepoCommitHash.equalsIgnoreCase(this.gitRepoCommitHash)) {
                throw new CodeFendException("Something went wrong. Please validate the branch name");
            }
        }
    }

    public String getGitRepoWebURL() {
        return gitRepoWebURL;
    }

    public String getGitRepoHttpUri() {
        return gitRepoHttpUri;
    }

    public String getGitRepoSshUri() {
        return gitRepoSshUri;
    }

    public String getGitRepoBranch() {
        return gitRepoBranch;
    }

    public String getGitRepoCommitHash() {
        return gitRepoCommitHash;
    }

    @Override
    public String toString() {
        return getGitRepoSlug();
    }
}