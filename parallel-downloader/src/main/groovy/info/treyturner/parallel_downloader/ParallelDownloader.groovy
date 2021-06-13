package info.treyturner.parallel_downloader

import static groovyx.gpars.GParsPool.*
import jsr166y.ForkJoinPool
import groovy.util.logging.Slf4j

/**
 * Download files in parallel
 */
@Slf4j
class ParallelDownloader {

    static final int POOL_SIZE = 32
    static ForkJoinPool pool

    ParallelDownloader() {
        pool = createPool(POOL_SIZE)
    }

    void download(Map fromTo, int maxConcurrent) {
        if (maxConcurrent > 0) {
            use(MapPartition) {
                List maps = fromTo.partition(maxConcurrent)
                maps.each { downloadMap ->
                    parallelDownload(downloadMap)
                }
            }
        } else {
            parallelDownload(fromTo)
        }
    }

    private void downloadFile(String remoteUrl, String localUrl) {
        log.debug "\nDownloading: $remoteUrl\n Local Path: $localUrl"
        File file = new File("$localUrl")
        file.parentFile.mkdirs()
        try {
            file.withOutputStream { out ->
                new URL(remoteUrl).withInputStream { from ->
                    out << from
                }
            }
        } catch (FileNotFoundException ignored) {
            log.warn "HTTP 404 (Not Found): $remoteUrl"
            file.delete()
        }
    }

    private void parallelDownload(Map fromTo) {
        withExistingPool(pool) {
            fromTo.eachParallel { from, to ->
                downloadFile(from, to)
            }
        }
    }

}

class MapPartition {

    static List partition(Map delegate, int size) {
        List result = delegate.inject([ [:] ]) { ret, elem ->
            (ret.last() << elem).size() >= size ?
                    ret << [:] : ret
        }
        result.last() ? result : result[0..-2]
    }

}
