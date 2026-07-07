package app.guitar.theory

/** A famous song built on — or characteristically featuring — a chord progression. */
data class SongExample(val title: String, val artist: String)

/**
 * Curated famous-song examples for the ear-training progression library. Tapping a
 * progression row in the library viewer reveals its list.
 *
 * Keys reuse the stable identifiers the library already uses, so a lookup can never
 * drift from what is displayed:
 *  - diatonic: the [Progression.degrees] list, split by [TrainingMode]
 *              (e.g. [1,4,5,1] exists in both major and minor),
 *  - advanced: the [EarTraining.NamedProgression.name],
 *  - circle:   the [EarTraining.CircleWindow.id] ("W1".."W7").
 *
 * Diatonic (major/minor) lists are ubiquitous, unambiguous hits. Advanced and circle
 * lists are *characteristic* examples — a song that prominently features the signature
 * harmonic move, not a promise of a note-for-note match; the UI labels those sections
 * accordingly. This is pure data + lookup: no UI or Android deps, unit-tested, and
 * shareable with the planned KMP iOS port.
 */
object ProgressionSongs {

    private fun s(title: String, artist: String) = SongExample(title, artist)

    /** Major diatonic, keyed by [Progression.degrees]. */
    val major: Map<List<Int>, List<SongExample>> = mapOf(
        listOf(1, 5, 6, 4) to listOf(   // I–V–vi–IV ("pop / axis")
            s("Let It Be", "The Beatles"),
            s("No Woman No Cry", "Bob Marley & The Wailers"),
            s("With or Without You", "U2"),
            s("Don't Stop Believin'", "Journey"),
            s("Someone Like You", "Adele"),
            s("I'm Yours", "Jason Mraz"),
            s("She Will Be Loved", "Maroon 5"),
            s("When I Come Around", "Green Day"),
            s("Take On Me", "a-ha"),
            s("Where Is the Love?", "The Black Eyed Peas"),
        ),
        listOf(1, 4, 5, 1) to listOf(   // I–IV–V–I (three-chord classic)
            s("Twist and Shout", "The Beatles"),
            s("La Bamba", "Ritchie Valens"),
            s("Wild Thing", "The Troggs"),
            s("Louie Louie", "The Kingsmen"),
            s("Johnny B. Goode", "Chuck Berry"),
            s("Blitzkrieg Bop", "Ramones"),
            s("Good Riddance (Time of Your Life)", "Green Day"),
            s("Hang On Sloopy", "The McCoys"),
            s("Blowin' in the Wind", "Bob Dylan"),
            s("I Fought the Law", "The Bobby Fuller Four"),
        ),
        listOf(1, 6, 4, 5) to listOf(   // I–vi–IV–V (50s doo-wop)
            s("Stand By Me", "Ben E. King"),
            s("Earth Angel", "The Penguins"),
            s("Every Breath You Take", "The Police"),
            s("Duke of Earl", "Gene Chandler"),
            s("Perfect", "Ed Sheeran"),
            s("Eternal Flame", "The Bangles"),
            s("Sherry", "The Four Seasons"),
            s("Runaround Sue", "Dion"),
            s("Unchained Melody", "The Righteous Brothers"),
            s("Baby", "Justin Bieber"),
        ),
        listOf(6, 4, 1, 5) to listOf(   // vi–IV–I–V (pop rotation)
            s("Zombie", "The Cranberries"),
            s("Save Tonight", "Eagle-Eye Cherry"),
            s("Boulevard of Broken Dreams", "Green Day"),
            s("Otherside", "Red Hot Chili Peppers"),
            s("Californication", "Red Hot Chili Peppers"),
            s("Numb", "Linkin Park"),
            s("Despacito", "Luis Fonsi & Daddy Yankee"),
        ),
        listOf(2, 5, 1, 1) to listOf(   // ii–V–I (jazz)
            s("Autumn Leaves", "Nat King Cole"),
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("Misty", "Erroll Garner"),
            s("Satin Doll", "Duke Ellington"),
            s("All the Things You Are", "Jerome Kern"),
            s("Tune Up", "Miles Davis"),
            s("Blue Bossa", "Kenny Dorham"),
            s("Take the \"A\" Train", "Duke Ellington"),
            s("Honeysuckle Rose", "Fats Waller"),
        ),
        listOf(1, 6, 2, 5) to listOf(   // I–vi–ii–V (rhythm-changes turnaround)
            s("Heart and Soul", "Hoagy Carmichael"),
            s("Blue Moon", "The Marcels"),
            s("I Got Rhythm", "George Gershwin"),
            s("This Boy", "The Beatles"),
            s("These Foolish Things", "Billie Holiday"),
            s("Since I Fell for You", "Lenny Welch"),
        ),
        listOf(1, 5, 1, 4) to listOf(   // I–V–I–IV (uncommon as a full loop)
            s("Me and Bobby McGee", "Janis Joplin"),
            s("Ob-La-Di, Ob-La-Da", "The Beatles"),
        ),
        listOf(1, 3, 4, 5) to listOf(   // I–iii–IV–V ("Puff" schema; rare as a loop)
            s("Puff, the Magic Dragon", "Peter, Paul and Mary"),
            s("Let's Get It On", "Marvin Gaye"),
        ),
        listOf(1, 5, 4, 1) to listOf(   // I–V–IV–I
            s("Bad Moon Rising", "Creedence Clearwater Revival"),
            s("Free Fallin'", "Tom Petty"),
            s("All the Small Things", "blink-182"),
            s("Take It Easy", "Eagles"),
            s("Knockin' on Heaven's Door", "Bob Dylan"),
        ),
        listOf(1, 3, 6, 4) to listOf(   // I–iii–vi–IV (soft tonic family)
            s("Someone Like You", "Adele"),
        ),
        listOf(6, 2, 5, 1) to listOf(   // vi–ii–V–I
            s("Island in the Sun", "Weezer"),
            s("It's My Life", "Talk Talk"),
            s("Fly Me to the Moon", "Frank Sinatra"),
        ),
        listOf(1, 2, 5, 1) to listOf(   // I–ii–V–I
            s("Cry Me a River", "Justin Timberlake"),
            s("Sunday Morning", "Maroon 5"),
        ),
    )

    /** Minor diatonic, keyed by [Progression.degrees]. */
    val minor: Map<List<Int>, List<SongExample>> = mapOf(
        listOf(1, 6, 3, 7) to listOf(   // i–VI–III–VII (minor "pop / axis")
            s("Save Tonight", "Eagle-Eye Cherry"),
            s("Zombie", "The Cranberries"),
            s("Numb", "Linkin Park"),
            s("Otherside", "Red Hot Chili Peppers"),
            s("Californication", "Red Hot Chili Peppers"),
            s("Self Esteem", "The Offspring"),
            s("Love the Way You Lie", "Eminem ft. Rihanna"),
            s("Wake Me Up", "Avicii"),
            s("Faded", "Alan Walker"),
            s("Despacito", "Luis Fonsi & Daddy Yankee"),
            s("Dragostea Din Tei", "O-Zone"),
            s("21 Guns", "Green Day"),
        ),
        listOf(1, 4, 5, 1) to listOf(   // i–iv–V–i (minor cadence)
            s("Since I've Been Loving You", "Led Zeppelin"),
            s("The Thrill Is Gone", "B.B. King"),
            s("Black Magic Woman", "Santana"),
            s("I Put a Spell on You", "Nina Simone"),
            s("Summertime", "Ella Fitzgerald"),
            s("Bésame Mucho", "Consuelo Velázquez"),
            s("St. James Infirmary Blues", "Louis Armstrong"),
            s("Hava Nagila", "Traditional"),
            s("We Three Kings", "Traditional"),
        ),
        listOf(1, 6, 7, 1) to listOf(   // i–VI–VII–i
            s("Livin' on a Prayer", "Bon Jovi"),
            s("You Give Love a Bad Name", "Bon Jovi"),
            s("It's My Life", "Bon Jovi"),
            s("Psycho Killer", "Talking Heads"),
            s("Toxic", "Britney Spears"),
            s("He's a Pirate", "Klaus Badelt"),
            s("Lux Aeterna", "Clint Mansell"),
        ),
        listOf(2, 5, 1, 1) to listOf(   // ii°–V–i (minor jazz cadence)
            s("Autumn Leaves", "Nat King Cole"),
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("Blue Bossa", "Kenny Dorham"),
            s("Black Orpheus (Manhã de Carnaval)", "Luiz Bonfá"),
            s("My Funny Valentine", "Chet Baker"),
            s("'Round Midnight", "Thelonious Monk"),
            s("Summertime", "Miles Davis"),
            s("Nature Boy", "Nat King Cole"),
            s("Stella by Starlight", "Miles Davis"),
            s("Softly, as in a Morning Sunrise", "John Coltrane"),
            s("Beautiful Love", "Bill Evans"),
        ),
        listOf(1, 7, 6, 5) to listOf(   // i–VII–VI–V (Andalusian descent)
            s("Hit the Road Jack", "Ray Charles"),
            s("Sultans of Swing", "Dire Straits"),
            s("Stray Cat Strut", "Stray Cats"),
            s("Runaway", "Del Shannon"),
            s("Happy Together", "The Turtles"),
            s("California Dreamin'", "The Mamas & the Papas"),
            s("Good Vibrations", "The Beach Boys"),
            s("Walk, Don't Run", "The Ventures"),
            s("Like a Hurricane", "Neil Young"),
        ),
        listOf(1, 4, 7, 3) to listOf(   // i–iv–VII–III (minor circle segment)
            s("I Will Survive", "Gloria Gaynor"),
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("You Never Give Me Your Money", "The Beatles"),
            s("All the Things You Are", "Jerome Kern"),
            s("Autumn Leaves", "Nat King Cole"),
            s("Still Got the Blues", "Gary Moore"),
            s("Nature Boy", "Nat King Cole"),
        ),
    )

    /** Advanced (named) progressions, keyed by [EarTraining.NamedProgression.name].
     *  Characteristic examples — the signature harmonic move, not a note-for-note match. */
    val advanced: Map<String, List<SongExample>> = mapOf(
        "Mixolydian Rocker" to listOf(   // I–bVII–IV
            s("Sweet Home Alabama", "Lynyrd Skynyrd"),
            s("Takin' Care of Business", "Bachman-Turner Overdrive"),
            s("Werewolves of London", "Warren Zevon"),
            s("Gloria", "Them"),
            s("Cocaine", "Eric Clapton"),
        ),
        "Bright Lift" to listOf(         // I–II–IV–I
            s("Eight Days a Week", "The Beatles"),
            s("Don't Think Twice, It's All Right", "Bob Dylan"),
            s("9 to 5", "Dolly Parton"),
            s("Tequila", "The Champs"),
        ),
        "Romantic Climax" to listOf(     // I–III–IV–iv
            s("Creep", "Radiohead"),
            s("Get Free", "Lana Del Rey"),
            s("In My Life", "The Beatles"),
            s("No Surprises", "Radiohead"),
        ),
        "Epic Backstep" to listOf(       // I–bVII–bVI–bVII
            s("Rolling in the Deep", "Adele"),
            s("Stairway to Heaven", "Led Zeppelin"),
            s("China Girl", "David Bowie"),
            s("All Along the Watchtower", "Jimi Hendrix"),
            s("Citizen Erased", "Muse"),
        ),
        "Andalusian Cadence" to listOf(  // i–bVII–bVI–V
            s("Hit the Road Jack", "Ray Charles"),
            s("Sultans of Swing", "Dire Straits"),
            s("Runaway", "Del Shannon"),
            s("Stray Cat Strut", "Stray Cats"),
            s("California Dreamin'", "The Mamas & the Papas"),
            s("Happy Together", "The Turtles"),
        ),
        "Dark Roots" to listOf(          // i–iv–v (natural-minor v)
            s("Back to Black", "Amy Winehouse"),
            s("Heartbreak Hotel", "Elvis Presley"),
            s("Ain't No Sunshine", "Bill Withers"),
            s("Bury a Friend", "Billie Eilish"),
        ),
        "Neo-Soul Minor" to listOf(      // i–v–bVI–bVII
            s("Redbone", "Childish Gambino"),
            s("The Bird", "Anderson .Paak"),
            s("Get You", "Daniel Caesar"),
            s("Location", "Khalid"),
        ),
        "Ragtime Circle" to listOf(      // I–VI7–II7–V7 (secondary-dominant chain)
            s("Sweet Georgia Brown", "Brother Bones & His Shadows"),
            s("Five Foot Two, Eyes of Blue", "The Ink Spots"),
            s("Alice's Restaurant", "Arlo Guthrie"),
            s("Hey! Baby", "Bruce Channel"),
            s("Salty Dog Blues", "Rev. Gary Davis"),
        ),
        "Classic Ragtime Turnaround" to listOf(   // I–I7–IV–iv
            s("Ain't She Sweet", "Gene Austin"),
            s("Bill Bailey, Won't You Please Come Home", "Bobby Darin"),
            s("When You're Smiling", "Louis Armstrong"),
            s("Baby Face", "Al Jolson"),
        ),
        "Chromatic Passing Chord" to listOf(      // I–#I°7–ii7–V7
            s("Ain't Misbehavin'", "Fats Waller"),
            s("I Got Rhythm", "George Gershwin"),
            s("Have You Met Miss Jones?", "Frank Sinatra"),
        ),
        "Traditional Rag Ending" to listOf(       // I–III7–IV–#IV°7–I/V–V7–I
            s("Maple Leaf Rag", "Scott Joplin"),
            s("The Entertainer", "Scott Joplin"),
            s("The Easy Winners", "Scott Joplin"),
            s("Solace", "Scott Joplin"),
            s("Twelfth Street Rag", "Euday L. Bowman"),
        ),
        "Melancholic Jazz-Rag" to listOf(         // I–III7–iv–ii7b5–V7
            s("Georgia on My Mind", "Ray Charles"),
            s("My Melancholy Baby", "Gene Austin"),
            s("After You've Gone", "Bessie Smith"),
            s("Somebody Stole My Gal", "Bing Crosby"),
        ),
        // ---- Advanced batch 2 (13–24) ----
        "Broadway Lift" to listOf(       // I–III7–IV–ii7–V7
            s("On the Sunny Side of the Street", "Louis Armstrong"),
            s("Georgia on My Mind", "Ray Charles"),
            s("Someday My Prince Will Come", "Snow White (Disney)"),
            s("Just a Closer Walk with Thee", "Traditional"),
        ),
        "Minor-Key Swing" to listOf(     // i–III7–iv–ii7–V7
            s("Bei Mir Bist Du Schön", "The Andrews Sisters"),
            s("Bésame Mucho", "Dean Martin"),
            s("Minor Swing", "Django Reinhardt"),
        ),
        "Extended Pop Ballad" to listOf( // I–III7–vi–IV–ii7–V7
            s("Come Rain or Come Shine", "Ray Charles"),
            s("There Will Never Be Another You", "Chet Baker"),
            s("Eternal Flame", "The Bangles"),
            s("The Shoop Shoop Song (It's in His Kiss)", "Cher"),
            s("One Call Away", "Charlie Puth"),
        ),
        "Tritone Substitution" to listOf(   // ii7–bII7–Imaj7
            s("The Girl from Ipanema", "Antônio Carlos Jobim"),
            s("Body and Soul", "Coleman Hawkins"),
            s("Night and Day", "Cole Porter"),
            s("Autumn Leaves", "Nat King Cole"),
        ),
        "Minor Line Cliché" to listOf(   // i–i(maj7)–i7–i6
            s("My Funny Valentine", "Chet Baker"),
            s("James Bond Theme", "Monty Norman"),
            s("Michelle", "The Beatles"),
            s("Stairway to Heaven", "Led Zeppelin"),
            s("This Masquerade", "George Benson"),
        ),
        "Romantic Plaintive" to listOf(  // I–Imaj7–I7–IV
            s("Something", "The Beatles"),
            s("Can't Take My Eyes Off You", "Frankie Valli"),
            s("I Just Called to Say I Love You", "Stevie Wonder"),
            s("Kiss Me", "Sixpence None the Richer"),
        ),
        "Church Cadence" to listOf(      // I–IV–I–bVII–IV
            s("Hey Jude", "The Beatles"),
            s("Sweet Home Alabama", "Lynyrd Skynyrd"),
            s("Takin' Care of Business", "Bachman-Turner Overdrive"),
            s("Fortunate Son", "Creedence Clearwater Revival"),
        ),
        "Gospel Walk-Up" to listOf(      // I–I/III–IV–#IV°7–V
            s("On the Sunny Side of the Street", "Louis Armstrong"),
            s("Embraceable You", "Ella Fitzgerald"),
            s("The Song Is You", "Frank Sinatra"),
        ),
        "Mario Cadence" to listOf(       // bVI–bVII–I
            s("Super Mario Bros. (Level Complete)", "Koji Kondo"),
            s("The Legend of Zelda: Ocarina of Time", "Koji Kondo"),
            s("Final Fantasy (Victory Fanfare)", "Nobuo Uematsu"),
            s("Star Wars (Main Title)", "John Williams"),
        ),
        "Royal Road" to listOf(          // IV–V–iii–vi
            s("Never Gonna Give You Up", "Rick Astley"),
            s("Titanium", "David Guetta & Sia"),
            s("Leave the Door Open", "Silk Sonic"),
            s("Fortnight", "Taylor Swift & Post Malone"),
            s("Heavy Rotation", "AKB48"),
            s("Yoru ni Kakeru", "YOASOBI"),
        ),
        "Bird Blues Turnaround" to listOf(   // Imaj7–#IV°7–iii7–VI7–ii7–V7
            s("Blues for Alice", "Charlie Parker"),
            s("Au Privave", "Charlie Parker"),
            s("Chi Chi", "Charlie Parker"),
            s("Bloomdido", "Charlie Parker"),
        ),
        "Montgomery Turnaround" to listOf(   // Imaj7–bIII7–bVI7–bII7
            s("Lady Bird", "Tadd Dameron"),
            s("Half Nelson", "Miles Davis"),
            s("Israel", "Miles Davis"),
        ),
        "Deceptive Cadence" to listOf(   // I–ii–V–vi
            s("Every Breath You Take", "The Police"),
            s("I Will", "The Beatles"),
            s("Take Me to Church", "Hozier"),
            s("Just Give Me a Reason", "Pink ft. Nate Ruess"),
            s("Thinking Out Loud", "Ed Sheeran"),
        ),
        "Applied V of V" to listOf(   // I–II7–V–I
            s("9 to 5", "Dolly Parton"),
            s("Eternal Flame", "The Bangles"),
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("Oh! Darling", "The Beatles"),
        ),
        "Tonicized Relative" to listOf(   // I–III7–vi–I
            s("Santeria", "Sublime"),
        ),
        "Applied V of ii" to listOf(   // I–VI7–ii–V–I
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("Sweet Georgia Brown", "Ben Bernie & Maceo Pinkard"),
        ),
        "Long Applied Turnaround" to listOf(   // I–III7–vi–II7–V–I
            s("Charleston", "James P. Johnson"),
            s("Hello! Ma Baby", "Howard & Emerson"),
            s("Alice's Restaurant", "Arlo Guthrie"),
            s("Hey! Baby", "Bruce Channel"),
            s("They're Red Hot", "Robert Johnson"),
        ),
        "Borrowed iv" to listOf(   // I–IV–iv–I
            s("Creep", "Radiohead"),
            s("If I Fell", "The Beatles"),
        ),
        "Mixolydian Vamp" to listOf(   // I–V–bVII–IV
            s("Sweet Home Alabama", "Lynyrd Skynyrd"),
            s("Can't You See", "The Marshall Tucker Band"),
            s("Werewolves of London", "Warren Zevon"),
        ),
        "bVI-bVII Climb" to listOf(   // I–bVI–bVII–I
            s("Lady Madonna", "The Beatles"),
            s("I Was Made to Love Her", "Stevie Wonder"),
        ),
        "Flat-Six Color" to listOf(   // I–bVI–IV–V
            s("I Saw Her Standing There", "The Beatles"),
            s("What a Wonderful World", "Louis Armstrong"),
            s("Peggy Sue", "Buddy Holly"),
            s("More Than a Feeling", "Boston"),
        ),
        "Flat-Three Borrowed" to listOf(   // I–bIII–IV–I
            s("Smells Like Teen Spirit", "Nirvana"),
        ),
        "Chromatic Descent" to listOf(   // I–iii–bIII–ii–V (characteristic descending-bass examples)
            s("My Funny Valentine", "Chet Baker"),
            s("Michelle", "The Beatles"),
        ),
        "Diminished to ii" to listOf(   // I–#I°–ii–V
            s("I Got Rhythm", "George Gershwin"),
            s("Oleo", "Sonny Rollins"),
            s("Rhythm-a-ning", "Thelonious Monk"),
        ),
        "Diminished to iii" to listOf(   // ii–#ii°–iii–VI7 (characteristic passing-diminished examples)
            s("Sweet Georgia Brown", "Ben Bernie & Maceo Pinkard"),
            s("Bill Bailey, Won't You Please Come Home", "Hughie Cannon"),
        ),
        "Minor #iv° to V" to listOf(   // i–#iv°–V–i (characteristic chromatic-approach examples)
            s("'Round Midnight", "Thelonious Monk"),
            s("Libertango", "Astor Piazzolla"),
        ),
        "Minor Plagal Diminished" to listOf(   // i–iv–#iv°–i (characteristic examples)
            s("Bésame Mucho", "Consuelo Velázquez"),
            s("Summertime", "George Gershwin"),
        ),
        "iii-VI-ii-V Turnaround" to listOf(   // iii7–VI7–ii7–V7
            s("There Will Never Be Another You", "Harry Warren"),
            s("Have You Met Miss Jones?", "Richard Rodgers"),
            s("All the Things You Are", "Jerome Kern"),
        ),
        "Rhythm-Changes Turnaround" to listOf(   // Imaj7–VI7–ii7–V7
            s("I Got Rhythm", "George Gershwin"),
            s("Anthropology", "Charlie Parker"),
            s("Cotton Tail", "Duke Ellington"),
            s("Salt Peanuts", "Dizzy Gillespie"),
        ),
        "Bossa Minor Diminished" to listOf(   // i–iv–#iv°–V7
            s("Corcovado (Quiet Nights of Quiet Stars)", "Antônio Carlos Jobim"),
            s("Manhã de Carnaval (Black Orpheus)", "Luiz Bonfá"),
        ),
        "Ragtime Return" to listOf(   // I–I7–IV–iv–I (characteristic ragtime/trad-jazz examples)
            s("Ain't Misbehavin'", "Fats Waller"),
            s("Bill Bailey, Won't You Please Come Home", "Hughie Cannon"),
            s("Five Foot Two, Eyes of Blue", "Ray Henderson"),
        ),
        "Bossa Chromatic" to listOf(   // Imaj7–#I°–ii7–V7
            s("The Girl from Ipanema", "Antônio Carlos Jobim"),
            s("Desafinado", "Antônio Carlos Jobim"),
            s("Só Danço Samba", "Antônio Carlos Jobim"),
        ),
        "Extended vi Turnaround" to listOf(   // I–vi–IV–iv–I (characteristic doo-wop + borrowed-iv examples)
            s("This Boy", "The Beatles"),
            s("Since I Don't Have You", "The Skyliners"),
        ),
        "Full Turnaround" to listOf(   // I–vi–ii–V–I
            s("Blue Moon", "Rodgers & Hart"),
            s("Heart and Soul", "Hoagy Carmichael & Frank Loesser"),
        ),
    )

    /** Circle-of-fifths windows, keyed by [EarTraining.CircleWindow.id]. Each of the
     *  seven draw-able windows is curated toward the songs whose loop best matches it,
     *  rather than repeating the same jazz standards across every window. */
    val circle: Map<String, List<SongExample>> = mapOf(
        "W1" to listOf(   // I – IV – vii° – iii
            s("Concerto in A minor, Op. 3 No. 8 (L'estro armonico)", "Antonio Vivaldi"),
            s("Brandenburg Concerto No. 2", "J.S. Bach"),
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("I Will Survive", "Gloria Gaynor"),
        ),
        "W2" to listOf(   // IV – vii° – iii – vi
            s("Autumn Leaves", "Nat King Cole"),
            s("Still Got the Blues", "Gary Moore"),
            s("You Never Give Me Your Money", "The Beatles"),
        ),
        "W3" to listOf(   // vii° – iii – vi – ii
            s("Autumn Leaves", "Nat King Cole"),
            s("I Will Survive", "Gloria Gaynor"),
            s("Still Got the Blues", "Gary Moore"),
        ),
        "W4" to listOf(   // iii – vi – ii – V
            s("I Got Rhythm", "George Gershwin"),
            s("Greatest Love of All", "Whitney Houston"),
            s("September", "Earth, Wind & Fire"),
        ),
        "W5" to listOf(   // vi – ii – V – I
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("I Will Survive", "Gloria Gaynor"),
            s("Still Got the Blues", "Gary Moore"),
            s("All the Things You Are", "Jerome Kern"),
            s("Europa (Earth's Cry Heaven's Smile)", "Santana"),
            s("Wild World", "Cat Stevens"),
        ),
        "W6" to listOf(   // ii – V – I – IV
            s("Autumn Leaves", "Nat King Cole"),
            s("All the Things You Are", "Jerome Kern"),
            s("You Never Give Me Your Money", "The Beatles"),
            s("Take the \"A\" Train", "Duke Ellington"),
            s("Blue Moon", "The Marcels"),
        ),
        "W7" to listOf(   // V – I – IV – vii°
            s("Fly Me to the Moon", "Frank Sinatra"),
            s("Autumn Leaves", "Nat King Cole"),
            s("Brandenburg Concerto No. 2", "J.S. Bach"),
            s("I Will Survive", "Gloria Gaynor"),
        ),
    )

    /** Songs for a diatonic [Progression] (major or minor); empty if none listed. */
    fun forDiatonic(p: Progression): List<SongExample> =
        (if (p.mode == TrainingMode.Major) major else minor)[p.degrees] ?: emptyList()

    /** Songs for an advanced progression by [EarTraining.NamedProgression.name]; empty if none. */
    fun forAdvanced(name: String): List<SongExample> = advanced[name] ?: emptyList()

    /** Songs for a circle-of-fifths window by [EarTraining.CircleWindow.id]; empty if none. */
    fun forCircleWindow(id: String): List<SongExample> = circle[id] ?: emptyList()
}
