package info.treyturner.leech_http_index

import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import info.treyturner.parallel_downloader.ParallelDownloader
import org.ccil.cowan.tagsoup.Parser
import groovy.util.logging.Slf4j

/**
 * Recursively crawl an HTTP index, downloading files matching a set of extensions
 */
@Slf4j
class LeechHttpIndex {

    static final List<String> EXTENSIONS = ['.mp3', '.txt', '.md5']
    static final String BASE_URL = 'http://hybridized.org/sets/'
    static final String LOCAL_BASE = 'Z:\\hybridized\\'
    static final int CONCURRENT_DOWNLOADS = 18

    static Map<String, String> fromTo = [:]

    static void main(String[] args) {
        processLink(BASE_URL)
        download()
    }

    static void processLink(String url) {
        GPathResult document = getXml(url)
        List<String> links = getChildLinks(document, url)
        List<String> downloads = getDownloadLinks(document, url)
        queueDownloads(downloads)
        links.each { processLink(it) }
    }

    static void queueDownloads(List<String> urls) {
        urls.each { url ->
            String localPath = transformPath(url)
            fromTo.put(url, localPath)
        }
    }

    static GPathResult getXml(String url) {
        String rootString = new URL(url).text
        XmlSlurper xmlSlurper = new XmlSlurper(new Parser())
        xmlSlurper.parseText(rootString)
    }

    static List<String> getChildLinks(GPathResult document, String url) {
        document.body.pre.a
                .findAll { isChildLink("${it.@href}") }
                .collect { url + it.@href }
    }

    static List<String> getDownloadLinks(GPathResult document, String url) {
        document.body.pre.a
                .findAll { isDownloadable("${it.@href}") }
                .collect { url + it.@href }
    }

    static boolean isDownloadable(String url) {
        EXTENSIONS.any { url.endsWith(it) }
    }

    static boolean isChildLink(String url) {
        url.endsWith('/') && url != '../'
    }

    static String transformPath(String url) {
        String relativePath = URLDecoder.decode(url.takeAfter(BASE_URL), 'UTF-8')
                .split('/').join(File.separator)
        LOCAL_BASE + relativePath
    }

    static void download() {
        ParallelDownloader pd = new ParallelDownloader()
        pd.download(fromTo, CONCURRENT_DOWNLOADS)
    }

}
