package info.treyturner.remove_redundant_music

import java.awt.Desktop
import java.nio.file.FileSystemException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import groovy.util.logging.Slf4j

/**
 * Scan a music library and remove lesser quality copies.
 * Assumes a lot about how my music folders are named
 */
@Slf4j
class RemoveRedundantMusic {

    static final int SECONDS_PER_MINUTE = 60

    static final Long DELETED_LIMIT = Long.MAX_VALUE
    static final Long PROCESSED_LIMIT = Long.MAX_VALUE

    static Long bytesFreed = 0
    static Long processedCount = 0
    static Long getDeletedCount() { (Long) removed.values().sum() }
    static Map<String, Long> removed = [flac: 0, '320': 0, v0: 0, v2: 0]
    static Desktop desktop = Desktop.getDesktop()

    static void main(String[] args) {
        File cwd = new File("${File.separator * 2}tron${File.separator}music")
        List<File> dirs = []
        log.debug 'Scanning directories...'
        LocalDateTime scanStart = LocalDateTime.now()
        cwd.eachDirRecurse { dirs.add(it) }
        LocalDateTime scanEnd = LocalDateTime.now()
        def (Long minutes, Long secs) = calculateScanTime(scanStart, scanEnd)
        log.debug "Found ${dirs.size()} directories in $minutes min $secs secs"
        for (dir in dirs) {
            if (processedCount < PROCESSED_LIMIT && deletedCount < DELETED_LIMIT) {
                if (isProcessable(dir)) {
                    processedCount++
//                    log.debug "Processing $processedCount: $dir.canonicalPath"
                    removeIfLesserVersion(dir)
                }
            } else {
                try {
                    assert processedCount < PROCESSED_LIMIT && deletedCount < DELETED_LIMIT
                } catch (e) {
                    throw new IllegalStateException("Reached processedCount of $PROCESSED_LIMIT " +
                            "or deletedCount of $DELETED_LIMIT", e)
                } finally {
                    logResults()
                }
            }
        }
        logResults()
    }

    static Boolean isProcessable(File dir) {
        dir.exists()
                && (dir.name.contains('{320}')
                        || dir.name.contains('{v0}')
                        || dir.name.contains('{v2}')
                        || (dir.name.contains('{flac}')
                                && !dir.name.contains('24bit')))
    }

    @SuppressWarnings(['DuplicateStringLiteral', 'NestedBlockDepth'])
    static void removeIfLesserVersion(File directory) {
        String currentFormat
        List superiorFormats = ['flac']
        switch (directory.name) {
            case { it.contains('{flac}') }:
                currentFormat = 'flac'
                break
            case { it.contains('{320}') }:
                currentFormat = '320'
                break
            case { it.contains('{v0}') }:
                currentFormat = 'v0'
                superiorFormats.add('320')
                break
            case { it.contains('{v2}') }:
                currentFormat = 'v2'
                superiorFormats.add('320')
                superiorFormats.add('v0')
                break
        }
        boolean betterCopyFound = false
        superiorFormats.each { superiorFormat ->
            if (!betterCopyFound) {
                String betterCopyPath
                if (currentFormat == 'flac') {
                    String albumName
                    if (directory.name.contains('(')) {
                        albumName = directory.name.takeBefore('(').trim()
                    } else {
                        albumName = directory.name.takeBefore('{').trim()
                    }
                    List peerDirList = getPeerDirectories(directory)
                    List hiResFiles = peerDirList.findAll { it.name.matches(/^${albumName}.*24bit.*\{flac}.*/) }
                    if (hiResFiles) {
                        if (directory.name.contains('Vinyl')) {
                            betterCopyPath = hiResFiles.find { it.name.contains('Vinyl') }?.canonicalPath
                        } else {
                            betterCopyPath = hiResFiles.find { !it.name.contains('Vinyl') }?.canonicalPath
                        }
                    }
                } else {
                    betterCopyPath = directory.canonicalPath.replace("{$currentFormat}", "{$superiorFormat}")
                }
                if (betterCopyPath) {
                    File betterCopy = new File(betterCopyPath)
                    if (betterCopy.exists()) {
                        log.debug "   Redundant copy: $directory.name"
                        log.debug "Made redundant by: $betterCopy.name"
                        bytesFreed += directory.directorySize()
                        removed[currentFormat]++
                        betterCopyFound = true
                        Boolean success = desktop.moveToTrash(directory)
                        if (!success) { throw new FileSystemException("Couldn't send to recycle bin") }
                    }
                }
            }
        }
    }

    @SuppressWarnings('DuplicateNumberLiteral')
    static String toHumanString(Long bytes) {
        Long base = 1024L
        Integer decimals = 3
        List prefix = ['', 'K', 'M', 'G', 'T']

        int i = Math.log(bytes) / Math.log(base) as Integer
        i = (i >= prefix.size() ? prefix.size() - 1 : i)
        Math.round((bytes / base ** i) * 10 ** decimals) / 10 ** decimals + prefix[i]
    }

    static List getPeerDirectories(File directory) {
        directory.parentFile.listFiles(new FilenameFilter() {

            @Override
            boolean accept(File current, String name) {
                new File(current, name).directory
            }

        })
    }

    static void logResults() {
        log.debug "Removed: $removed"
        log.debug "Total size: ${toHumanString(bytesFreed)}"
    }

    static List<Long> calculateScanTime(LocalDateTime start, LocalDateTime end) {
        long seconds = ChronoUnit.SECONDS.between(start, end)
        Long secs = seconds % SECONDS_PER_MINUTE
        BigDecimal minutes = (seconds - secs) / SECONDS_PER_MINUTE
        [minutes, secs]
    }

}
