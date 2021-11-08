package info.treyturner.report_movie_genres

import com.opencsv.CSVWriter
import groovy.io.FileType
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Paths

@Slf4j
class ReportMovieGenres {

    static void main(String[] args) {
        def scanDir = new File('Y:\\Unsorted\\')
        def movies = []

        scanDir.eachFile FileType.DIRECTORIES, { dir ->
            log.debug "Parsing {}", dir.name
            def parenIdxs = [
                    open: dir.name.lastIndexOf('('),
                    close: dir.name.lastIndexOf(')')
            ]

            if (parenIdxs.open && parenIdxs.close) {
                def title = dir.name[0..parenIdxs.open - 2]
                def year = Integer.parseInt(dir.name[parenIdxs.open + 1..parenIdxs.close - 1])

                if (title.endsWith(', The')) {
                    title = "The ${title[0..-6]}"
                } else if (title.endsWith(', A')) {
                    title = "A ${title[0..-4]}"
                }

                movies << [title: title, year: year, dir: dir.name]
            } else {
                log.warn "Couldn't parse year: $dir.name"
            }
        }

        Files.newBufferedWriter(Paths.get('./unsorted.csv')).withWriter { writer ->
            CSVWriter csvWriter = new CSVWriter(writer,
                    CSVWriter.DEFAULT_SEPARATOR,
                    CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                    CSVWriter.DEFAULT_LINE_END
            )

            String[] headerRecord = ["Title", "Genre 1", "Genre 2", "Genre 3"]
            csvWriter.writeNext(headerRecord)

            movies.each { movie ->
                log.debug "Looking up $movie.title ($movie.year)"
                def record = OmdbClient.getMovie(movie.title, movie.year).body
                if (record.Response == "")
                List<String> row = [movie.dir]
                if (record.Response == "True" && record.Genre) {
                    def genres = record.Genre.tokenize(', ')
                    3.times { row.add(genres?[it] ?: "") }
                } else {
                    row.add("NOT FOUND!")
                    row.add("Check year")
                    row.add("and title")
                }

                log.debug "row: {}", row
                csvWriter.writeNext(row as String[])
            }

            csvWriter.close()
        }
    }

}
