package info.treyturner.mamefilter

import groovy.util.logging.Slf4j
import groovy.xml.XmlParser
import groovy.xml.XmlUtil

/**
 * Filter MAME ROMs not eliminated by RomLister
 */
@Slf4j
class MameFilter {

    final static String YES = 'yes'
    final static String NO = 'no'

    static File sourceXml = new File('src/main/resources/mame0238.xml')
    static File outputXml = new File('src/main/resources/mame0238_filtered.xml')

    static Map filters = [
            // Not runnable, bios, device, mechanical
            'Not Runnable': { it.@runnable == NO },
            'BIOS': { it.@isbios == YES },
            'Device': { it.@isdevice == YES },
            'Mechanical': { it.@ismechanical == YES },

            // Clones
            'Clone': { it.@cloneof },

            // Blacklisted Manufacturers
            'Blacklisted Manufacturer': {
                it.manufacturer.text() =~ /Aristocrat/
                        || it.manufacturer.text() =~ /BFM/
                        || it.manufacturer.text() =~ /Cal Omega Inc\./
                        || it.manufacturer.text() =~ /CGI/
                        || it.manufacturer.text() =~ /Conic/
                        || it.manufacturer.text() =~ /Entex/
                        || it.manufacturer.text() =~ /Gakken/
                        || it.manufacturer.text() =~ /IGT - International Game Technology/
                        || it.manufacturer.text() =~ /JAKKS Pacific Inc/
                        || it.manufacturer.text() =~ /Merit/
                        || it.manufacturer.text() =~ /WinFun/
            },

            // Computers, etc.
            'Software List': { it.device_ref.any { it.@name == 'software_list' } },
            'Cassette System': { it.device.any { it.@type == 'cassette' } },
            'DECO Cassette': { it.description.text() =~ /DECO Cassette/ },
            'Floppy System': { it.device.any { it.@type == 'floppydisk' } },
            'CD Updater': { it.description.text() =~ /CD-ROM Drive Updater/ },

            // Unusable controllers
            'Keypad Controller': { it.input.control.any { it.@type == 'keypad' } },
            'Keyboard Controller': { it.input.control.any { it.@type == 'keyboard' } },
            'Music Controller': {
                it.description.text() =~ /(?i)Keyboardmania/
                || it.description.text() =~ /(?i)Beatmania/
            },

            // Tile
            'Mahjong': {
                it.description.text() =~ /(?i)Mahjong/
                || it.input.control.any { it.@type == 'mahjong' }
            },

            // Card
            'Hanafuda': { it.description.text() =~ /(?i)Hanafuda/ },
            'Card': { it.description.text() =~ /(?i)Card/ },
            'Blackjack': { it.description.text() =~ /(?i)Blackjack/ },
            'Hold\'em': { it.description.text() =~ /(?i)Hold ?'?em/ },
            'Poker': { it.description.text() =~ /(?i)Poker/ },

            // Consoles in arcade format
            'PlayChoice': { it.description.text() =~ /PlayChoice/ },
            'Nintendo Super System': { it.description.text() =~ /Nintendo Super System/ },
            'Super Famicom Box': { it.description.text() =~ /Super Famicom Box/ },

            // Plug'n'play TV games
            'Plug and Play': { it.description.text() =~ /(?i)Plug ?(?:and|&|'?n'?) ?Play/ },
            'TV Game': { it.description.text() =~ /(?i)TV Game/ },
            'Play TV': { it.description.text() =~ /(?i)Play TV/ },
            'TV Play': { it.description.text() =~ /(?i)TV Play/ },
            'ConnecTV': { it.description.text() =~ /(?i)ConnecTV/ },

            // Handhelds
            'Handheld': {
                it.description.text() =~ /(?i)Handheld/
                || it.chip.any {
                    it.@type == 'cpu'
                    && it.@tag == 'maincpu'
                    && (
                        it.@name == 'Texas Instruments TMS1100'
                        || it.@name == 'Hitachi HD38800'
                        || it.@name == 'SPG240-series System-on-a-Chip'
                        || it.@name == 'MOS Technology 6502'
                    )
                }
            },

            // Multigame
            'Multigame': {
                it.description.text() =~ /(?i)[ -]in[ -]1/
                || it.description.text() =~ /(?i)multi[ -]?game/ },

            // Casino & gambling
            'Casino': { it.description.text() =~ /(?i)Casino/ },
            'Keno': { it.description.text() =~ /(?i)Keno/ },
            'Bingo': { it.description.text() =~ /(?i)Bingo/ },
            'Dice': { it.descrition.text() =~ /(?i)Dice/ },
            'Lotto': { it.description.text() =~ /(?i)Lotto/ },
            'Slot': { it.description.text() =~ /(?i)Slot/ },
            'Fruit': {
                it.description.text() =~ /(?i)Fruit/
                && it.description.text() != 'Fruit Land' },
            'Backgammon': { it.description.text() =~ /(?i)Backgammon/ },
            'Gambling Control': { it.input.control.any { it.@type == 'gambling' } },

            // Hunting
            'Hunting': { it.description.text() =~ /(?i)Huntin['g]/ },

            // Darts
            'Dart': { it.description.text() =~ /(?i)Dart\b/ },

            // Quiz & trivia
            'Quiz': { it.description.text() =~ /(?i)Quiz/ },
            'Trivia': { it.description.text() =~ /(?i)Trivia/ },
            'Sudoku': { it.description.text() =~ /(?i)Sudoku/ },

            // No controls
            'No Controls': { !it.input.control },

            // Zero player
            'Zero Player': { it.input.@players.contains('0') },

            // Test
            'Test': { it.description.text() =~ /(?i)Test/ },
            'Diagnostic': { it.description.text() =~ /(?i)Diagnostic/ },

            // (Ping?) Pong
            'Pong': { it.description.text() =~ /(?i)\bPong\b/ },

            // Catch anything else that slipped through
            'Blacklist': {
                it.description.text() =~ /(?i)Nixie Clock/
                || it.description.text() =~ /Sony PocketStation/
            },

            // Bootlegs
            'Bootleg': { it.description.text() =~ /(?i)bootleg/ },

            // Pedal controls
//            'Pedal': { it.input.control.any { it.@type == 'pedal' } },

            // More than 7 buttons
            '8 buttons+': {
                it.input.control.any {
                    it.@type == 'joy'
                    && it.@buttons
                    && Integer.parseInt(it.@buttons) > 7
                }
            },
    ]

    static void main(String[] args) {
        Node xml = parseInput()
        xml = filterRoms(xml)
        writeOutput(xml)
    }

    static Node parseInput() {
        String sourceText = sourceXml.text
        sourceText = sourceText.replaceAll(/\r\n$/, /\n/)
        sourceText = sourceText.replaceAll(/name="(.*)<(.*)"/, /name="$1&lt;$2"/)
        XmlParser parser = new XmlParser()
        parser.setFeature('http://apache.org/xml/features/disallow-doctype-decl', false)
        Node xml = parser.parseText(sourceText)
        Integer inputRoms = xml.children().size()
        log.info "Input: $inputRoms ROMs"
        xml
    }

    static Node filterRoms(Node xml) {
        Node filtered = xml
        filters.each { name, closure ->
            Collection<Node> found = xml.'**'.machine.findAll(closure)
            log.info '{}', "Filter '$name' caught ${found.size()} ROMs"
            found.each {
                log.debug "\tRemoving ${it.description.text()}"
                filtered.remove(it)
            }
        }
        filtered
    }

    static void writeOutput(Node xml) {
        Integer outputRoms = xml.children().size()
        log.info "Output: $outputRoms ROMs"
        String outputText = XmlUtil.serialize(xml)
        outputText = outputText.replaceAll('(?m)^ +\r?\n', '')
        outputXml.write(outputText)
    }

}
