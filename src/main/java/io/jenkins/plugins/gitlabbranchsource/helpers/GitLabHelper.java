package io.jenkins.plugins.gitlabbranchsource.helpers;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.damnhandy.uri.template.UriTemplate;
import com.damnhandy.uri.template.UriTemplateBuilder;
import com.damnhandy.uri.template.impl.Operator;
import hudson.ProxyConfiguration;
import hudson.security.AccessControlled;
import io.jenkins.plugins.gitlabserverconfig.credentials.PersonalAccessToken;
import io.jenkins.plugins.gitlabserverconfig.servers.GitLabServer;
import io.jenkins.plugins.gitlabserverconfig.servers.GitLabServers;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.eclipse.jgit.annotations.NonNull;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.ProxyClientConfig;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

public class GitLabHelper {

    public static GitLabApi apiBuilder(AccessControlled context, String serverName) {
        GitLabServer server = GitLabServers.get().findServer(serverName);
        if (server != null) {
            StandardCredentials credentials = server.getCredentials(context);
            String serverUrl = server.getServerUrl();
            String privateToken = getPrivateTokenAsPlainText(credentials);
            if (privateToken.equals(GitLabServer.EMPTY_TOKEN)) {
                api = new GitLabApi(serverUrl, GitLabServer.EMPTY_TOKEN, null, getProxyConfig(serverUrl));
                api.enableRequestResponseLogging();
                return api;
            } else {
                api = new GitLabApi(serverUrl, privateToken, null, getProxyConfig(serverUrl));
                api.enableRequestResponseLogging();
                return api;
            }
        }
        throw new IllegalStateException(String.format("No server found with the name: %s", serverName));
    }

    public static Map<String, Object> getProxyConfig(String serverUrl) {
        ProxyConfiguration proxyConfiguration = Jenkins.get().getProxy();
        if (proxyConfiguration != null) {
            final URL url;
            try {
                url = new URL(serverUrl);
            } catch (MalformedURLException e) {
                // let it crash somewhere else
                return null;
            }
            if (!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol())) {
                // non-http(s) URL, proxy won't handle it
                return null;
            }
            List<Pattern> nonProxyHostPatterns = proxyConfiguration.getNoProxyHostPatterns();
            if (nonProxyHostPatterns.stream()
                    .anyMatch(p -> p.matcher(url.getHost()).matches())) {
                // target host is excluded by proxy configuration
                return null;
            }
            if (proxyConfiguration.getUserName() != null && proxyConfiguration.getSecretPassword() != null) {
                return ProxyClientConfig.createProxyClientConfig(
                        "http://" + proxyConfiguration.getName() + ":" + proxyConfiguration.getPort(),
                        proxyConfiguration.getUserName(),
                        proxyConfiguration.getSecretPassword().getPlainText());
            }
            return ProxyClientConfig.createProxyClientConfig(
                    "http://" + proxyConfiguration.getName() + ":" + proxyConfiguration.getPort());
        }
        return null;
    }

    @NonNull
    public static String getServerUrlFromName(String serverName) {
        GitLabServer server = GitLabServers.get().findServer(serverName);
        return getServerUrl(server);
    }

    @NonNull
    public static String getServerUrl(GitLabServer server) {
        if (server == null) {
            return GitLabServer.GITLAB_SERVER_URL;
        }
        String url = server.getServerUrl();
        return sanitizeUrlValue(url);
    }

    @NonNull
    private static String getServerUrl(String server) {
        if (server.startsWith("http://") || server.startsWith("https://")) {
            return sanitizeUrlValue(server);
        } else {
            return getServerUrlFromName(server);
        }
    }

    private static String sanitizeUrlValue(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String getPrivateTokenAsPlainText(StandardCredentials credentials) {
        String privateToken = "";
        if (credentials != null) {
            if (credentials instanceof PersonalAccessToken) {
                privateToken = ((PersonalAccessToken) credentials).getToken().getPlainText();
            }
            if (credentials instanceof StringCredentials) {
                privateToken = ((StringCredentials) credentials).getSecret().getPlainText();
            }
        }
        return privateToken;
    }

    public static UriTemplateBuilder getUriTemplateFromServer(String server) {
        return UriTemplate.buildFromTemplate(getServerUrl(server));
    }

    public static UriTemplate projectUriTemplate(String serverNameOrUrl) {
        return getUriTemplateFromServer(serverNameOrUrl).template("{/project*}").build();
    }

    public static UriTemplate branchUriTemplate(String serverNameOrUrl) {
        return getUriTemplateFromServer(serverNameOrUrl)
                .template("{/project*}/-/tree/{branch*}")
                .build();
    }

    public static UriTemplate mergeRequestUriTemplate(String serverNameOrUrl) {
        return getUriTemplateFromServer(serverNameOrUrl)
                .template("{/project*}/-/merge_requests/{iid}")
                .build();
    }

    public static UriTemplate tagUriTemplate(String serverNameOrUrl) {
        return getUriTemplateFromServer(serverNameOrUrl)
                .template("{/project*}/-/tree/{tag*}")
                .build();
    }

    public static UriTemplate commitUriTemplate(String serverNameOrUrl) {
        return getUriTemplateFromServer(serverNameOrUrl)
                .template("{/project*}/-/commit/{hash}")
                .build();
    }

    public static String[] splitPath(String path) {
        return path.split(Operator.PATH.getSeparator());
    }
}
