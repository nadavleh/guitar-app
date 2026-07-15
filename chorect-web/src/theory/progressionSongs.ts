// Famous-song examples for the ear-training progression library. Mirror of
// theory/.../ProgressionSongs.kt — keep the two in sync (Kotlin can't be imported
// into the web build). Diatonic lists are ubiquitous hits; advanced and circle
// lists are *characteristic* examples of the signature harmonic move (the UI labels
// those sections accordingly), not a promise of a note-for-note match.

import { Progression, TrainingMode } from "./eartraining";

/** A famous song built on — or characteristically featuring — a chord progression. */
export interface SongExample { title: string; artist: string; }

const s = (title: string, artist: string): SongExample => ({ title, artist });

/** Major diatonic, keyed by `degrees.join(",")`. */
const MAJOR: Record<string, SongExample[]> = {
  "1,5,6,4": [
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
  ],
  "1,4,5,1": [
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
  ],
  "1,6,4,5": [
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
  ],
  "6,4,1,5": [
    s("Zombie", "The Cranberries"),
    s("Save Tonight", "Eagle-Eye Cherry"),
    s("Boulevard of Broken Dreams", "Green Day"),
    s("Otherside", "Red Hot Chili Peppers"),
    s("Californication", "Red Hot Chili Peppers"),
    s("Numb", "Linkin Park"),
    s("Despacito", "Luis Fonsi & Daddy Yankee"),
  ],
  "2,5,1,1": [
    s("Autumn Leaves", "Nat King Cole"),
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("Misty", "Erroll Garner"),
    s("Satin Doll", "Duke Ellington"),
    s("All the Things You Are", "Jerome Kern"),
    s("Tune Up", "Miles Davis"),
    s("Blue Bossa", "Kenny Dorham"),
    s('Take the "A" Train', "Duke Ellington"),
    s("Honeysuckle Rose", "Fats Waller"),
  ],
  "1,6,2,5": [
    s("Heart and Soul", "Hoagy Carmichael"),
    s("Blue Moon", "The Marcels"),
    s("I Got Rhythm", "George Gershwin"),
    s("This Boy", "The Beatles"),
    s("These Foolish Things", "Billie Holiday"),
    s("Since I Fell for You", "Lenny Welch"),
  ],
  "1,5,1,4": [
    s("Me and Bobby McGee", "Janis Joplin"),
    s("Ob-La-Di, Ob-La-Da", "The Beatles"),
  ],
  "1,3,4,5": [
    s("Puff, the Magic Dragon", "Peter, Paul and Mary"),
    s("Let's Get It On", "Marvin Gaye"),
  ],
  "1,5,4,1": [
    s("Bad Moon Rising", "Creedence Clearwater Revival"),
    s("Free Fallin'", "Tom Petty"),
    s("All the Small Things", "blink-182"),
    s("Take It Easy", "Eagles"),
    s("Knockin' on Heaven's Door", "Bob Dylan"),
  ],
  "1,3,6,4": [
    s("Someone Like You", "Adele"),
    s("The Greatest Show", "The Greatest Showman Cast"),
  ],
  "6,2,5,1": [
    s("Island in the Sun", "Weezer"),
    s("It's My Life", "Talk Talk"),
    s("Fly Me to the Moon", "Frank Sinatra"),
  ],
  "1,2,5,1": [
    s("Cry Me a River", "Justin Timberlake"),
    s("Sunday Morning", "Maroon 5"),
  ],
  "1,4,2,5": [
    s("You're Still the One", "Shania Twain"),
    s("Love Bites", "Def Leppard"),
  ],
  "1,4,6,5": [
    s("She Drives Me Crazy", "Fine Young Cannibals"),
    s("Where the Streets Have No Name", "U2"),
  ],
  "1,5,4,5": [
    s("Wild World", "Cat Stevens"),
    s("Waking Up in Vegas", "Katy Perry"),
  ],
  "6,5,4,5": [
    s("Daniel", "Elton John"),
    s("Come Sail Away", "Styx"),
  ],
};

/** Minor diatonic, keyed by `degrees.join(",")`. */
const MINOR: Record<string, SongExample[]> = {
  "1,6,3,7": [
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
  ],
  "1,4,5,1": [
    s("Since I've Been Loving You", "Led Zeppelin"),
    s("The Thrill Is Gone", "B.B. King"),
    s("Black Magic Woman", "Santana"),
    s("I Put a Spell on You", "Nina Simone"),
    s("Summertime", "Ella Fitzgerald"),
    s("Bésame Mucho", "Consuelo Velázquez"),
    s("St. James Infirmary Blues", "Louis Armstrong"),
    s("Hava Nagila", "Traditional"),
    s("We Three Kings", "Traditional"),
  ],
  "1,6,7,1": [
    s("Livin' on a Prayer", "Bon Jovi"),
    s("You Give Love a Bad Name", "Bon Jovi"),
    s("It's My Life", "Bon Jovi"),
    s("Psycho Killer", "Talking Heads"),
    s("Toxic", "Britney Spears"),
    s("He's a Pirate", "Klaus Badelt"),
    s("Lux Aeterna", "Clint Mansell"),
  ],
  "2,5,1,1": [
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
  ],
  "1,7,6,5": [
    s("Hit the Road Jack", "Ray Charles"),
    s("Sultans of Swing", "Dire Straits"),
    s("Stray Cat Strut", "Stray Cats"),
    s("Runaway", "Del Shannon"),
    s("Happy Together", "The Turtles"),
    s("California Dreamin'", "The Mamas & the Papas"),
    s("Good Vibrations", "The Beach Boys"),
    s("Walk, Don't Run", "The Ventures"),
    s("Like a Hurricane", "Neil Young"),
  ],
  "1,4,7,3": [
    s("I Will Survive", "Gloria Gaynor"),
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("You Never Give Me Your Money", "The Beatles"),
    s("All the Things You Are", "Jerome Kern"),
    s("Autumn Leaves", "Nat King Cole"),
    s("Still Got the Blues", "Gary Moore"),
    s("Nature Boy", "Nat King Cole"),
  ],
  "1,5,6,7": [
    s("Jenny Was a Friend of Mine", "The Killers"),
    s("Reptilia", "The Strokes"),
    s("High Hopes", "Pink Floyd"),
  ],
  "1,3,7,4": [
    s("Just Dance", "Lady Gaga"),
    s("SOS", "ABBA"),
    s("Personal Jesus", "Depeche Mode"),
  ],
};

/** Advanced (named) progressions, keyed by NamedProgression.name. */
const ADVANCED: Record<string, SongExample[]> = {
  "Mixolydian Rocker": [
    s("Sweet Home Alabama", "Lynyrd Skynyrd"),
    s("Takin' Care of Business", "Bachman-Turner Overdrive"),
    s("Werewolves of London", "Warren Zevon"),
    s("Gloria", "Them"),
    s("Cocaine", "Eric Clapton"),
  ],
  "Bright Lift": [
    s("Eight Days a Week", "The Beatles"),
    s("Don't Think Twice, It's All Right", "Bob Dylan"),
    s("9 to 5", "Dolly Parton"),
    s("Tequila", "The Champs"),
  ],
  "Romantic Climax": [
    s("Creep", "Radiohead"),
    s("Get Free", "Lana Del Rey"),
    s("In My Life", "The Beatles"),
    s("No Surprises", "Radiohead"),
  ],
  "Epic Backstep": [
    s("Rolling in the Deep", "Adele"),
    s("Stairway to Heaven", "Led Zeppelin"),
    s("China Girl", "David Bowie"),
    s("All Along the Watchtower", "Jimi Hendrix"),
    s("Citizen Erased", "Muse"),
  ],
  "Andalusian Cadence": [
    s("Hit the Road Jack", "Ray Charles"),
    s("Sultans of Swing", "Dire Straits"),
    s("Runaway", "Del Shannon"),
    s("Stray Cat Strut", "Stray Cats"),
    s("California Dreamin'", "The Mamas & the Papas"),
    s("Happy Together", "The Turtles"),
    s("Smooth Criminal", "Michael Jackson"),
    s("Good Vibrations", "The Beach Boys"),
  ],
  "Dark Roots": [
    s("Back to Black", "Amy Winehouse"),
    s("Heartbreak Hotel", "Elvis Presley"),
    s("Ain't No Sunshine", "Bill Withers"),
    s("Bury a Friend", "Billie Eilish"),
  ],
  "Neo-Soul Minor": [
    s("Redbone", "Childish Gambino"),
    s("The Bird", "Anderson .Paak"),
    s("Get You", "Daniel Caesar"),
    s("Location", "Khalid"),
  ],
  "Ragtime Circle": [
    s("Sweet Georgia Brown", "Brother Bones & His Shadows"),
    s("Five Foot Two, Eyes of Blue", "The Ink Spots"),
    s("Alice's Restaurant", "Arlo Guthrie"),
    s("Hey! Baby", "Bruce Channel"),
    s("Salty Dog Blues", "Rev. Gary Davis"),
  ],
  "Classic Ragtime Turnaround": [
    s("Ain't She Sweet", "Gene Austin"),
    s("Bill Bailey, Won't You Please Come Home", "Bobby Darin"),
    s("When You're Smiling", "Louis Armstrong"),
    s("Baby Face", "Al Jolson"),
  ],
  "Chromatic Passing Chord": [
    s("Ain't Misbehavin'", "Fats Waller"),
    s("I Got Rhythm", "George Gershwin"),
    s("Have You Met Miss Jones?", "Frank Sinatra"),
  ],
  "Traditional Rag Ending": [
    s("Maple Leaf Rag", "Scott Joplin"),
    s("The Entertainer", "Scott Joplin"),
    s("The Easy Winners", "Scott Joplin"),
    s("Solace", "Scott Joplin"),
    s("Twelfth Street Rag", "Euday L. Bowman"),
  ],
  "Melancholic Jazz-Rag": [
    s("Georgia on My Mind", "Ray Charles"),
    s("My Melancholy Baby", "Gene Austin"),
    s("After You've Gone", "Bessie Smith"),
    s("Somebody Stole My Gal", "Bing Crosby"),
  ],
  "Broadway Lift": [
    s("On the Sunny Side of the Street", "Louis Armstrong"),
    s("Georgia on My Mind", "Ray Charles"),
    s("Someday My Prince Will Come", "Snow White (Disney)"),
    s("Just a Closer Walk with Thee", "Traditional"),
  ],
  "Minor-Key Swing": [
    s("Bei Mir Bist Du Schön", "The Andrews Sisters"),
    s("Bésame Mucho", "Dean Martin"),
    s("Minor Swing", "Django Reinhardt"),
  ],
  "Extended Pop Ballad": [
    s("Come Rain or Come Shine", "Ray Charles"),
    s("There Will Never Be Another You", "Chet Baker"),
    s("Eternal Flame", "The Bangles"),
    s("The Shoop Shoop Song (It's in His Kiss)", "Cher"),
    s("One Call Away", "Charlie Puth"),
  ],
  "Tritone Substitution": [
    s("The Girl from Ipanema", "Antônio Carlos Jobim"),
    s("Body and Soul", "Coleman Hawkins"),
    s("Night and Day", "Cole Porter"),
    s("Autumn Leaves", "Nat King Cole"),
  ],
  "Minor Line Cliché": [
    s("My Funny Valentine", "Chet Baker"),
    s("James Bond Theme", "Monty Norman"),
    s("Michelle", "The Beatles"),
    s("Stairway to Heaven", "Led Zeppelin"),
    s("This Masquerade", "George Benson"),
  ],
  "Romantic Plaintive": [
    s("Something", "The Beatles"),
    s("Can't Take My Eyes Off You", "Frankie Valli"),
    s("I Just Called to Say I Love You", "Stevie Wonder"),
    s("Kiss Me", "Sixpence None the Richer"),
  ],
  "Church Cadence": [
    s("Hey Jude", "The Beatles"),
    s("Sweet Home Alabama", "Lynyrd Skynyrd"),
    s("Takin' Care of Business", "Bachman-Turner Overdrive"),
    s("Fortunate Son", "Creedence Clearwater Revival"),
  ],
  "Gospel Walk-Up": [
    s("On the Sunny Side of the Street", "Louis Armstrong"),
    s("Embraceable You", "Ella Fitzgerald"),
    s("The Song Is You", "Frank Sinatra"),
  ],
  "Mario Cadence": [
    s("Super Mario Bros. (Level Complete)", "Koji Kondo"),
    s("The Legend of Zelda: Ocarina of Time", "Koji Kondo"),
    s("Final Fantasy (Victory Fanfare)", "Nobuo Uematsu"),
    s("Star Wars (Main Title)", "John Williams"),
  ],
  "Royal Road": [
    s("Never Gonna Give You Up", "Rick Astley"),
    s("Titanium", "David Guetta & Sia"),
    s("Leave the Door Open", "Silk Sonic"),
    s("Fortnight", "Taylor Swift & Post Malone"),
    s("Heavy Rotation", "AKB48"),
    s("Yoru ni Kakeru", "YOASOBI"),
  ],
  "Bird Blues Turnaround": [
    s("Blues for Alice", "Charlie Parker"),
    s("Au Privave", "Charlie Parker"),
    s("Chi Chi", "Charlie Parker"),
    s("Bloomdido", "Charlie Parker"),
  ],
  "Montgomery Turnaround": [
    s("Lady Bird", "Tadd Dameron"),
    s("Half Nelson", "Miles Davis"),
    s("Israel", "Miles Davis"),
  ],
  "Deceptive Cadence": [
    s("Every Breath You Take", "The Police"),
    s("I Will", "The Beatles"),
    s("Take Me to Church", "Hozier"),
    s("Just Give Me a Reason", "Pink ft. Nate Ruess"),
    s("Thinking Out Loud", "Ed Sheeran"),
  ],
  "Applied V of V": [
    s("9 to 5", "Dolly Parton"),
    s("Eternal Flame", "The Bangles"),
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("Oh! Darling", "The Beatles"),
  ],
  "Tonicized Relative": [
    s("Santeria", "Sublime"),
    s("Georgia on My Mind", "Ray Charles"),
  ],
  "Applied V of ii": [
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("Sweet Georgia Brown", "Ben Bernie & Maceo Pinkard"),
  ],
  "Long Applied Turnaround": [
    s("Charleston", "James P. Johnson"),
    s("Hello! Ma Baby", "Howard & Emerson"),
    s("Alice's Restaurant", "Arlo Guthrie"),
    s("Hey! Baby", "Bruce Channel"),
    s("They're Red Hot", "Robert Johnson"),
  ],
  "Borrowed iv": [
    s("Creep", "Radiohead"),
    s("If I Fell", "The Beatles"),
    s("Breathe Again", "Toni Braxton"),
    s("Space Oddity", "David Bowie"),
  ],
  "Mixolydian Vamp": [
    s("Sweet Home Alabama", "Lynyrd Skynyrd"),
    s("Can't You See", "The Marshall Tucker Band"),
    s("Werewolves of London", "Warren Zevon"),
  ],
  "bVI-bVII Climb": [
    s("Lady Madonna", "The Beatles"),
    s("I Was Made to Love Her", "Stevie Wonder"),
  ],
  "Flat-Six Color": [
    s("I Saw Her Standing There", "The Beatles"),
    s("What a Wonderful World", "Louis Armstrong"),
    s("Peggy Sue", "Buddy Holly"),
    s("More Than a Feeling", "Boston"),
  ],
  "Flat-Three Borrowed": [
    s("Smells Like Teen Spirit", "Nirvana"),
    s("Purple Haze", "Jimi Hendrix"),
    s("After Midnight", "J.J. Cale"),
    s("Thank You (Falettinme Be Mice Elf Agin)", "Sly & the Family Stone"),
    s("Will It Go Round in Circles", "Billy Preston"),
  ],
  "Chromatic Descent": [
    s("My Funny Valentine", "Chet Baker"),
    s("Michelle", "The Beatles"),
  ],
  "Diminished to ii": [
    s("I Got Rhythm", "George Gershwin"),
    s("Oleo", "Sonny Rollins"),
    s("Rhythm-a-ning", "Thelonious Monk"),
  ],
  "Diminished to iii": [
    s("Sweet Georgia Brown", "Ben Bernie & Maceo Pinkard"),
    s("Bill Bailey, Won't You Please Come Home", "Hughie Cannon"),
  ],
  "Minor #iv° to V": [
    s("'Round Midnight", "Thelonious Monk"),
    s("Libertango", "Astor Piazzolla"),
  ],
  "Minor Plagal Diminished": [
    s("Bésame Mucho", "Consuelo Velázquez"),
    s("Summertime", "George Gershwin"),
  ],
  "iii-VI-ii-V Turnaround": [
    s("There Will Never Be Another You", "Harry Warren"),
    s("Have You Met Miss Jones?", "Richard Rodgers"),
    s("All the Things You Are", "Jerome Kern"),
  ],
  "Rhythm-Changes Turnaround": [
    s("I Got Rhythm", "George Gershwin"),
    s("Anthropology", "Charlie Parker"),
    s("Cotton Tail", "Duke Ellington"),
    s("Salt Peanuts", "Dizzy Gillespie"),
  ],
  "Bossa Minor Diminished": [
    s("Corcovado (Quiet Nights of Quiet Stars)", "Antônio Carlos Jobim"),
    s("Manhã de Carnaval (Black Orpheus)", "Luiz Bonfá"),
  ],
  "Ragtime Return": [
    s("Ain't Misbehavin'", "Fats Waller"),
    s("Bill Bailey, Won't You Please Come Home", "Hughie Cannon"),
    s("Five Foot Two, Eyes of Blue", "Ray Henderson"),
  ],
  "Bossa Chromatic": [
    s("The Girl from Ipanema", "Antônio Carlos Jobim"),
    s("Desafinado", "Antônio Carlos Jobim"),
    s("Só Danço Samba", "Antônio Carlos Jobim"),
  ],
  "Extended vi Turnaround": [
    s("This Boy", "The Beatles"),
    s("Since I Don't Have You", "The Skyliners"),
  ],
  "Full Turnaround": [
    s("Blue Moon", "Rodgers & Hart"),
    s("Heart and Soul", "Hoagy Carmichael & Frank Loesser"),
  ],
  // ---- folded in from the Top-96 list ----
  "Pachelbel's Canon": [s("Canon in D", "Johann Pachelbel"), s("Basket Case", "Green Day"), s("Don't Look Back in Anger", "Oasis")],
  "Minor ii–V–i": [s("You Never Give Me Your Money", "The Beatles"), s("Love You Like a Love Song", "Selena Gomez")],
  "Neapolitan Cadence": [s("Frozen", "Madonna")],
  // ---- SUS category ----
  "Sus Resolution": [s("We Can Work It Out", "The Beatles")],
  "Suspended Lift": [s("We Are Never Ever Getting Back Together", "Taylor Swift")],
  "Sus Bookends": [s("House of Cards", "Radiohead")],
  "Dorian Sus Vamp": [s("Sun", "Caribou")],
  "Mixolydian Sus": [s("Lightning Bolt", "Jake Bugg")],
  // ---- ADVANCED II category (maj7 / min9 / modal) ----
  "Maj7 Pop": [s("Believe", "Cher")],
  "Maj7 Climb": [s("Haven't Met You Yet", "Michael Bublé")],
  "Backdoor Maj7": [s("My Cherie Amour", "Stevie Wonder")],
  "Minor-9 Vamp": [s("Fake Plastic Trees", "Radiohead")],
  "Add9 Roots": [s("The Way It Is", "Bruce Hornsby")],
  "Dorian Vamp": [s("Song 2", "Blur")],
  "Mixolydian Two": [s("Take It or Leave It", "The Strokes")],
  "Lydian Bright": [s("Man on the Moon", "R.E.M.")],
  "Phrygian Dark": [s("London Calling", "The Clash")],
};

/** Circle-of-fifths windows, keyed by CircleWindow.id ("W1".."W7"). */
const CIRCLE: Record<string, SongExample[]> = {
  W1: [
    s("Concerto in A minor, Op. 3 No. 8 (L'estro armonico)", "Antonio Vivaldi"),
    s("Brandenburg Concerto No. 2", "J.S. Bach"),
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("I Will Survive", "Gloria Gaynor"),
  ],
  W2: [
    s("Autumn Leaves", "Nat King Cole"),
    s("Still Got the Blues", "Gary Moore"),
    s("You Never Give Me Your Money", "The Beatles"),
  ],
  W3: [
    s("Autumn Leaves", "Nat King Cole"),
    s("I Will Survive", "Gloria Gaynor"),
    s("Still Got the Blues", "Gary Moore"),
  ],
  W4: [
    s("I Got Rhythm", "George Gershwin"),
    s("Greatest Love of All", "Whitney Houston"),
    s("September", "Earth, Wind & Fire"),
  ],
  W5: [
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("I Will Survive", "Gloria Gaynor"),
    s("Still Got the Blues", "Gary Moore"),
    s("All the Things You Are", "Jerome Kern"),
    s("Europa (Earth's Cry Heaven's Smile)", "Santana"),
    s("Wild World", "Cat Stevens"),
  ],
  W6: [
    s("Autumn Leaves", "Nat King Cole"),
    s("All the Things You Are", "Jerome Kern"),
    s("You Never Give Me Your Money", "The Beatles"),
    s('Take the "A" Train', "Duke Ellington"),
    s("Blue Moon", "The Marcels"),
  ],
  W7: [
    s("Fly Me to the Moon", "Frank Sinatra"),
    s("Autumn Leaves", "Nat King Cole"),
    s("Brandenburg Concerto No. 2", "J.S. Bach"),
    s("I Will Survive", "Gloria Gaynor"),
  ],
};

/** PDF-imported extras (Song artist chords v03.pdf), shown behind a "Show more"
 *  expander in the Songs popup — kept SEPARATE from the curated MAJOR lists above
 *  so the curated hits show first. Keyed by `degrees.join(",")` like MAJOR. */
const MAJOR_IMPORTED: Record<string, SongExample[]> = {
  "1,5,6,4": [
    s("20 Good Reasons", "Thirsty Merc"),
    s("Afterlife", "Avenged Sevenfold"),
    s("All Too Well", "Taylor Swift"),
    s("Always on My Mind", "Brenda Lee"),
    s("Am I Not Too Sweet", "Natural Born Hippies"),
    s("Androgynous", "The Replacements"),
    s("Another Girl, Another Planet", "The Only Ones"),
    s("Auld Lang Syne", "Robert Burns / Traditional"),
    s("Beast of Burden", "The Rolling Stones"),
    s("Bridge of Light", "P!nk"),
    s("Brighter", "Against the Current"),
    s("Bullet", "Hollywood Undead"),
    s("California King Bed", "Rihanna"),
    s("Caramelldansen", "Caramell"),
    s("Ciaccona", "Antonio Bertali"),
    s("Cruise", "Florida Georgia Line"),
    s("Cryin'", "Aerosmith"),
    s("Cute Without The 'E' (Cut from The Team)", "Taking Back Sunday"),
    s("Dammit", "Blink-182"),
    s("Demons", "Imagine Dragons"),
    s("Dirty Little Secret", "The All-American Rejects"),
    s("Don't Stop the Dancing", "DJ Manian"),
    s("Down Under", "Men at Work"),
    s("Dream Catch Me", "Newton Faulkner"),
    s("Feeling This", "Blink-182"),
    s("Flashlight", "Jessie J"),
    s("For the First Time", "The Script"),
    s("Forever Young", "Alphaville"),
    s("Four Chords", "The Axis of Awesome"),
    s("Fuckin' Perfect", "P!nk"),
  ],
  "1,6,4,5": [
    s("A Teenager in Love", "Dion and the Belmonts; Doc Pomus, Mort Shuman (writers)"),
    s("All I Have to Do Is Dream", "Everly Brothers"),
    s("All I Want for Christmas Is You", "Mariah Carey"),
    s("Angel Baby", "Rosie and the Originals"),
    s("Baby, I'm an Anarchist!", "Against Me!"),
    s("Beautiful Girls", "Sean Kingston"),
    s("Beyond the Sea", "Jack Lawrence and Charles Trenet"),
    s("Brave as a Noun", "Andrew Jackson Jihad"),
    s("Bristol Stomp", "The Dovells"),
    s("Capital Radio", "The Clash"),
    s("Chain Gang", "Sam Cooke"),
    s("Close Your Eyes", "Meghan Trainor"),
    s("Cradle Rock", "The Heartbreakers (Ray Collins/ Frank Zappa)"),
    s("Crocodile Rock", "Elton John"),
    s("Da Doo, Dentist", "Little Shop of Horrors"),
    s("Dance with Me Tonight", "Olly Murs"),
    s("Dear Future Husband", "Meghan Trainor"),
    s("Donna", "Ritchie Valens"),
    s("Double Shot (Of My Baby's Love)", "The Swingin' Medallions"),
    s("D'yer Mak'er", "Led Zeppelin"),
    s("Enola Gay", "Orchestral Manoeuvres in the Dark"),
    s("Eyes of Blue", "Paul Carrack"),
    s("Eyes on Me", "Faye Wong"),
    s("Flightless Bird, American Mouth", "Iron & Wine"),
    s("For Your Precious Love", "Jerry Butler"),
    s("Friday", "Rebecca Black"),
    s("Go Cry On Somebody Else's Shoulder", "Mothers of Invention"),
    s("God Is in the Rhythm", "King Gizzard & the Lizard Wizard"),
    s("Happiness Is a Warm Gun", "The Beatles"),
    s("I Always Knew", "The Vaccines"),
  ],
  "6,4,1,5": [
    s("21st Century (Digital Boy)", "Bad Religion"),
    s("40 Reasons", "Assembly of Dust"),
    s("45", "The Gaslight Anthem"),
    s("4Ever", "The Veronicas"),
    s("81", "Joanna Newsom"),
    s("9 Crimes", "Damien Rice"),
    s("A Drop in The Ocean", "Ron Pope"),
    s("A Guy with A Girl", "Blake Shelton"),
    s("A Little Too Not Over You", "David Archuleta"),
    s("A Man Like Putin (Такого как Путин)", "Poyushchie vmeste (Поющие вместе)"),
    s("A New Day Has Come", "Celine Dion"),
    s("A Wonderful Life", "Brian Fallon"),
    s("Acela", "Fountains Of Wayne"),
    s("Africa", "Toto"),
    s("Aicha", "Cheb Khaled"),
    s("Ain't Your Mama", "Jennifer Lopez"),
    s("Airplanes", "B.o.B"),
    s("All Cried Out", "Kree Harrison"),
    s("All Day", "Cody Simpson"),
    s("All For Love", "Serena Ryder"),
    s("All Good Things", "The Weepies"),
    s("All Hail The Kings Of Trash", "Matthew Ryan"),
    s("All I Ever Wanted", "Basshunter"),
    s("All Of Me", "John Legend"),
    s("All Of That Means Nothing Now", "Matthew Ryan"),
    s("All of the Above", "Maino ft. T-Pain"),
    s("All That Matters", "Addison Road"),
    s("All These Things", "Lori McKenna"),
    s("All Those Notes", "The Clean"),
    s("All We Are", "OneRepublic"),
  ],
  "1,6,2,5": [
    s("You Are Not Alone", "Michael Jackson"),
  ],
};

/** PDF-imported EXTRA songs for a diatonic progression (major only); [] otherwise. */
export function importedSongsForDiatonic(p: Progression): SongExample[] {
  if (p.mode !== TrainingMode.Major) return [];
  return MAJOR_IMPORTED[p.degrees.join(",")] ?? [];
}

/** Songs for a diatonic progression (major or minor); [] if none listed. */
export function songsForDiatonic(p: Progression): SongExample[] {
  const table = p.mode === TrainingMode.Major ? MAJOR : MINOR;
  return table[p.degrees.join(",")] ?? [];
}

/** Songs for an advanced progression by name; [] if none listed. */
export function songsForAdvanced(name: string): SongExample[] {
  return ADVANCED[name] ?? [];
}

/** Songs for a circle-of-fifths window by id; [] if none listed. */
export function songsForCircleWindow(id: string): SongExample[] {
  return CIRCLE[id] ?? [];
}
