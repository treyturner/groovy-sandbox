package info.treyturner.rename_files_to_dir_name

import groovy.io.FileType
import groovy.util.logging.Slf4j

/**
 * Rename all files in a directory to the name of the parent directory, replacing spaces with periods.
 * Useful when a downloaded usenet post has its filenames scrambled
 */
@Slf4j
class RenameFilesToDirName {

    static void main(String[] args) {
        File root = new File("Z:${File.separator}temp")
        root.eachFile(FileType.DIRECTORIES) { dir ->
            String name = dir.name.replaceAll(/ /, /./).replace(/DD2.0/, /DD+2.0/)
            dir.eachFile(FileType.FILES) { file ->
                String extension = file.name.split(/\./)[1]
                file.renameTo("$file.parent$File.separator$name.$extension")
            }
        }
    }

}
