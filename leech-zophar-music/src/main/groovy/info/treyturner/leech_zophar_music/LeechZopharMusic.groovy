package info.treyturner.leech_zophar_music

import groovy.util.logging.Slf4j
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import info.treyturner.parallel_downloader.ParallelDownloader
import org.ccil.cowan.tagsoup.Parser

/**
 * Leech entire systems worth of music from Zophar's Domain music collection
 */
@Slf4j
class LeechZopharMusic {

    static final String REMOTE_BASE_URL = 'https://fi.zophar.net/soundfiles/'
    static final String LOCAL_BASE = 'Z:\\zophar\\'
    static final Integer CONCURRENT_DOWNLOADS = 6
    static final Integer SYSTEM_LIMIT = Integer.MAX_VALUE
    static final Integer INDEX_LIMIT = Integer.MAX_VALUE

    static Integer systemsProcessed = 0
    static Integer indexesProcessed = 0

    static final List<String> SYSTEM_URLS = [
            'https://www.zophar.net/music/nintendo-nes-nsf',
            'https://www.zophar.net/music/gameboy-gbs',
            'https://www.zophar.net/music/sega-game-gear-sgc',
            'https://www.zophar.net/music/sega-master-system-vgm',
            'https://www.zophar.net/music/sega-mega-drive-genesis',
            'https://www.zophar.net/music/turbografx-16-hes',
            'https://www.zophar.net/music/nintendo-snes-spc',
            'https://www.zophar.net/music/gameboy-advance-gsf',
            'https://www.zophar.net/music/nintendo-64-usf',
            'https://www.zophar.net/music/nintendo-ds-2sf',
            'https://www.zophar.net/music/nintendo-3ds-3sf',
            'https://www.zophar.net/music/playstation-psf',
            'https://www.zophar.net/music/sega-dreamcast-dsf',
            'https://www.zophar.net/music/playstation-portable-psp',
            'https://www.zophar.net/music/xbox',
            'https://www.zophar.net/music/nintendo-gamecube-gcn',
            'https://www.zophar.net/music/sega-saturn-ssf',
            'https://www.zophar.net/music/nintendo-wii',
            'https://www.zophar.net/music/playstation2-psf2',
            'https://www.zophar.net/music/playstation3-psf3',
            'https://www.zophar.net/music/xbox-360',
    ]

    static void main(String[] args) {
        SYSTEM_URLS.each { systemUrl ->
            assert systemsProcessed < SYSTEM_LIMIT
            log.debug "Processing system $systemUrl"
            List<String> indexUrls = getIndexUrls(systemUrl)
            indexUrls.each { indexUrl ->
                assert indexesProcessed < INDEX_LIMIT
                List<String> gameUrls = getGameUrls(indexUrl)
                List<String> downloadUrls = []
                gameUrls.each { gameUrl ->
                    String downloadUrl = getDownloadUrl(gameUrl)
                    if (downloadUrl) {
                        downloadUrls.add(downloadUrl)
                    }
                }
                Map<String, String> fromTo = generateFromTo(downloadUrls)
                download(fromTo)
                indexesProcessed++
            }
            systemsProcessed++
        }
    }

    static GPathResult getXml(String url) {
        String string = new URL(url).text
        XmlSlurper xmlSlurper = new XmlSlurper(new Parser())
        xmlSlurper.parseText(string)
    }

    static Integer getIndexCount(String systemUrl) {
        log.debug "Getting indexCount from $systemUrl"
        GPathResult document = getXml(systemUrl)
        Integer indexCount
        GPathResult pagination = document.body.'**'.find { it.name() == 'div' && it.@class.text() == 'pagination' }
        if (pagination) {
            GPathResult counter = pagination.'**'.find { it.name() == 'p' && it.@class.text() == 'counter' }
            indexCount = Integer.parseInt(counter.text().trim().split(/ /)[3])
        } else {
            indexCount = 1
        }
        log.debug "indexCount: $indexCount"
        indexCount
    }

    static List<String> getIndexUrls(String systemUrl) {
        log.debug "Generating index URLs for $systemUrl"
        Integer indexCount = getIndexCount(systemUrl)
        List<String> indexUrls = [systemUrl]
        if (indexCount > 1) {
            (2..indexCount).each { index ->
                indexUrls.add(systemUrl + "?page=$index")
            }
        }
        log.debug "indexUrls:\n${indexUrls.join('\n')}"
        indexUrls
    }

    static List<String> getGameUrls(String indexUrl) {
        log.debug "Getting game URLs from $indexUrl"
        GPathResult document = getXml(indexUrl)
        GPathResult table = document.body.'**'.find { it.@id == 'gamelist' }
        List<GPathResult> games = table.'**'.findAll { it.name() == 'tr' && it.@class.text().contains('regularrow') }
        List<String> gameUrls = games.collect { game ->
            GPathResult name = game.'**'.find { it.name() == 'td' && it.@class.text() == 'name' }
            GPathResult link = name.'**'.find { it.name() == 'a' }
            'https://zophar.net' + link.@href.text()
        }
//        log.debug "gameUrls: ${gameUrls.join('\n')}"
        gameUrls
    }

    static String getDownloadUrl(String gameUrl) {
        log.debug "Getting download URL from $gameUrl"
        GPathResult document = getXml(gameUrl)
        GPathResult massDownload = document.body.'**'.find { it.@id == 'mass_download' }
        GPathResult downloadText = massDownload.'**'.find {
                it.name() == 'p' &&
                it.text().contains('Download original music files')
        }
        String downloadUrl
        if (downloadText) {
            List<GPathResult> children = massDownload.children().list()
            Integer idx = children.indexOf(downloadText)
            GPathResult link = children[--idx]
            downloadUrl = link.@href.text()
        }
        log.debug "downloadUrl: $downloadUrl"
        downloadUrl
    }

    static Map generateFromTo(List<String> downloadUrls) {
        Map fromTo = [:]
        downloadUrls.each { downloadUrl ->
            String localPath = transformPath(downloadUrl)
            File localFile = new File(localPath)
            if (!localFile.exists()) {
                fromTo.put(downloadUrl, localPath)
            }
        }
        fromTo
    }

    static String transformPath(String url) {
        String relativePath = URLDecoder.decode(url.takeAfter(REMOTE_BASE_URL), 'UTF-8')
        List<String> splitPath = relativePath.split('/')
        splitPath.remove(1)
        String localRelative = splitPath.join(File.separator)
        LOCAL_BASE + localRelative
    }

    static void download(Map<String, String> fromTo) {
        if (fromTo) {
            ParallelDownloader pd = new ParallelDownloader()
            if (fromTo.size() < CONCURRENT_DOWNLOADS) {
                pd.download(fromTo, fromTo.size())
            } else {
                pd.download(fromTo, CONCURRENT_DOWNLOADS)
            }
        }
    }

}
