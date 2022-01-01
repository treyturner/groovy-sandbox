package info.treyturner.report_movie_genres

import com.opencsv.CSVWriter
import groovy.io.FileType
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Generate a spreadsheet of movies in my unsorted folder with their
 * genres enumerated so as to aid in classification and sorting
 */
@Slf4j
class ReportMovieGenres {

    static final Integer GENRE_COUNT = 5

    static void main(String[] args) {
        File scanDir = new File("Y:${File.separator}Unsorted${File.separator}")
        List<Map<String, String>> movies = []

        scanDir.eachFile FileType.DIRECTORIES, { dir ->
            log.debug 'Parsing {}', dir.name
            Map<String, Integer> parenIdxs = [
                    open: dir.name.lastIndexOf('('),
                    close: dir.name.lastIndexOf(')'),
            ]

            if (parenIdxs.open && parenIdxs.close) {
                String title = dir.name[0..parenIdxs.open - 2]
                Integer year = Integer.parseInt(dir.name[parenIdxs.open + 1..parenIdxs.close - 1])

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

            List<String> headerRecord = ['Title']
            GENRE_COUNT.times {
                headerRecord.add("Genre ${it + 1}")
            }
            csvWriter.writeNext(headerRecord as String[])

            movies.each { movie ->
                log.debug "Looking up $movie.title ($movie.year)"
                Map record = OmdbClient.getMovie(movie.title, movie.year)
                List<String> row = [movie.dir]
                if (record.Response == 'True' && record.Genre) {
                    List<String> genres = record.Genre.tokenize(', ')
                    GENRE_COUNT.times { row.add(genres?[it] ?: '') }
                } else {
                    row.add('NOT FOUND!')
                    row.add('Check year')
                    row.add('and title')
                    (GENRE_COUNT - 3).times { row.add('') }
                }

                log.debug 'row: {}', row
                csvWriter.writeNext(row as String[])
            }

            csvWriter.close()
        }
    }

}
