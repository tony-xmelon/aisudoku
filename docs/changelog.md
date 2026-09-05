# Changelog

Every round of changes, newest first. This file is for reading; it is not what gets
uploaded to App Distribution.

`docs/release-notes.txt` carries the notes for the CURRENT release only, and is capped
by Firebase at 16,384 characters. It used to hold all of this, growing every round, and
one day it went over the limit and the upload failed after a full CI build had already
run. Keep that file short and put the history here.

THE PUZZLE SCREEN MEETS THE PUZZLES IT NEVER USED TO SEE.
Three faults with one cause, and the two features that were missing behind them.

Letting an unsolvable puzzle through to the screen sent it something it had never been
given before. Every one of Tony's complaints follows from that, except the first, which
is mine.

The four buttons stood half the screen tall. A pill draws its hint fill inside the
button, in a box that fills the height it is given - and a button is given whatever its
parent allows, which in that row is the rest of the screen. Filling a bounded height is
what was meant. The pill is now 40dp and the box fills that.

Solve did nothing, because it drew the answer only when there was exactly one:
`Solver.solve(grid) as? SolveResult.Unique`. The one case where a person most needs to
be told something was the case where they were told least. All three outcomes now draw.

With more than one answer it shows one of them, exactly as it shows the answer to a
proper puzzle, and rings the squares the answers disagree about - which are precisely
the ones a missed printed digit would have settled. Pressing Solve again steps to the
next rather than putting the layer away, up to six, because there is nowhere on that
screen to put a pair of arrows worth the room and the button is already under the thumb.

With no answer at all it names the squares to fix. "No solution" is true and useless;
the useful answer is which digits were misread. [MinimalFix] looks for the smallest set
of givens that, taken away, leaves a puzzle that solves - one first, then pairs, trying
squares that already clash with another before the rest. Removing rather than changing
is the same question asked more cheaply: if the rest solves without it, the solution
says what belongs there. It stops at two, because three is hundreds of thousands of
solves on a phone while somebody waits, and a page needing three is one to photograph
again.

The tutor drawer is absent on both, and that is correct rather than broken - the tutor
walks a route to *the* answer and neither has one. What was wrong is that nothing said
so. Both status lines now say what to do rather than only what is wrong, and both point
at Solve.


ONLY LOSING THE GRID STOPS THE APP NOW.
The same brittleness at three stages in a row, each one throwing away a grid it had
already recognised.

The rule Tony asked for is that nothing but failing to find the grid should stop the
pipeline - whatever is read or misread can be corrected on the main screen. Applying
it to the advertisement photograph turned up the same shape of mistake three times.

The reader treated an unsolvable puzzle as an unreadable one. Fixed last round.

The score is the weakest of the twenty places a grid line must appear, which is strict
on purpose and has a consequence that is not: one line behind a thumb, a fold or a
highlight takes the whole score down with it. Nineteen lines present, one covered,
0.22 against the 0.35 needed - and 0.22 again cropped to the screen alone, so it was
the picture and not the clutter round it. One line an axis may now be missing, and only
as the last question asked. Not for ranking: relaxing it there was tried and cost a
corpus page, because a worse quad with no single terrible line outranks the right one
and the grid that comes back cannot be fitted. Two lines were tried too, and buy
nothing - 0.45 against 0.46 - while lowering the bar for everything.

Then the line fitter did it again one stage later: all ten lines had to be found or the
fit failed outright, so the photograph was lost after the locator had recognised it. A
grid is regular, so a rule nobody can see is still at a position anybody can compute.
Up to two an axis are now put where the grid says they must be.

The photograph is in the corpus and it is not read well: the grid is found at 0.45 and
straightened, most of the puzzle comes back, and about thirty of its squares hold ink
too washed out to find at all - not misread, never found. Retraining on it took its
printed digits from 671 of 697 to 695, and handwriting across the whole corpus to
753 of 753. Reaching the missing thirty by lowering the ink threshold was measured and
makes everything worse, because what a lower bar reaches first is noise rather than
faint digits: four sorts 1884 cells of 2025, three sorts 1882, two sorts 1868.

Its cells are counted apart from the collision rather than added to it. Two different
faults summed into one number is how the collision came to be blamed for a third of the
cells it had nothing to do with, and once was enough.


A PUZZLE THAT CANNOT BE SOLVED IS STILL A PUZZLE THAT WAS READ.
One rule, and the photograph that made it obvious.

The reader had come to treat solvability as part of recognition. When the printed
digits did not make a puzzle with one solution, it tried a few repairs, and if more
than six cells were still in doubt it reported the whole photograph unreadable. That
is the wrong thing to say about a page whose digits it has in fact read correctly, and
it is the wrong thing to do to the person holding the phone, who is shown nothing and
told nothing about what the app saw.

Tony sent an advertisement - a phone on a car seat showing "Play Sudoku. Only IQ=180
can solve it.." - whose puzzle nobody can solve, because it has many solutions rather
than one. Forty-five printed digits in a neat staircase, nine in the first row down to
one in the last, all perfectly legible. There is nothing useful about answering that
with "could not read".

So only a failure of recognition stops the pipeline now: no printed digits found, or
too few of them to be a puzzle. Anything that was read is handed on with the doubtful
cells attached, and what cannot be solved is still shown. The immediate effect is that
newsprint-blue-3, the one corpus page that came back as nothing, now comes back as a
grid - which is also why the collision ceiling rises from 100 to 110, since its cells
are counted here for the first time. Nothing that was already counted moved.

The advertisement itself is not in the corpus yet, and that is the honest half of this
entry. Its grid cannot be located: 0.27 against the 0.35 needed, and 0.27 again when
cropped to the screen alone, so it is the picture rather than the clutter round it -
thin light rules drawn on a screen, photographed out of focus at an angle, with a tan
overlay across the lower left that takes the left-hand columns with it. Its
transcription is committed and verified, in the odd way this page has to be verified:
no row, column or box repeats a digit, and the staircase is its own check, but there
is no unique solution to check the givens against because there is no unique solution.
The photograph goes into the corpus on the day the locator can see it.


A THIRD OF WHAT THE TRIAGE GOT WRONG WAS NOT THE COLLISION AT ALL.
Twenty-five cells recovered, and a measured answer to how far the rest can go.

The print/handwriting collision has been the standing explanation for every cell the
triage sorts wrongly. Counted by kind rather than in one lump, it is not: of 125 wrong
cells, 74 were handwriting taken for print - the collision proper - and 47 were
written answers thrown away as pencilled candidate marks. Two faults wearing one
number, and the second is the easier one.

Its cause is an assumption stated plainly in the code and false on this newsprint: an
answer had to be taller than the print. Twenty-eight of those 47 answers are *smaller*
than the print beside them. Size cannot carry that decision, and the measurements say
it does not have to - marks run along the top of the cell and are pencil, carrying
0.011 of the print's ink at the median and 0.255 at the ninetieth percentile, where
answers sit centred and are pen, from 0.22 up. Dropping the size floor from 1.10 to
0.80 and letting position and ink do the work takes the triage from 1738 of 1863 cells
to 1763, with nothing on any page that read correctly before now reading wrongly.

0.80 is a trade rather than an optimum, and worth naming as one: 0.75 leaves eighteen
of these cells wrong instead of twenty-two, and costs one cell on a page that is
correct today. Where those two meet is where it stopped.

The collision proper is harder, and it is now measured rather than guessed at. Held
out photograph by photograph, no rule over the six things the reader measures of a
blob - height, position, ink, contrast, stroke width, company - does better than about
seventy of the 79 cells on that boundary. A linear rule gets 70, product terms 70, a
decision tree 71. A small learned function over the same six numbers gets 33. So the
signal is there and it is not expressible as a threshold, which is the useful finding:
the next step on the collision is a model, not another constant.

Two things were tried against the collision and did not work, recorded so they are not
tried again. The puzzle's own logic cannot arbitrate it: a false given is a redundant
given, so removing givens that uniqueness does not need would find them - except that
these newspaper puzzles are not minimal. Every one of Tony's ten is; every one of the
twelve newsprint pages has between 8 and 22 givens that could be removed without
making the puzzle ambiguous. And matching each digit against a template of the same
digit elsewhere on the page - print being a font, handwriting not - separates the two
almost perfectly when the templates are built from the true print, at 7 wrong in the
whole corpus, and does no better than the existing rule when they are built from what
the reader currently believes. It is a good idea waiting on a better seed.


THE CURLED PAGE READS. THE THING WRONG WITH IT WAS NOT THE CURVE.
This closes the entry below it, which said the fix was not ready. It is, and the
reason it was not is worth more than the fix.

Every cell of a photograph is given a place in the nine by nine, and that was written
as though a place were not a scarce thing: each cell rounded its position to the
nearest place on its own, and when two cells rounded onto the same one the second was
dropped by a single line - `if (!taken.add(place)) continue` - with nothing anywhere
reporting it. Rounding cannot express this, because the constraint is between cells
rather than inside one. Now every cell's claim on every place is measured, the nearest
claim is settled first, and a cell whose place has gone takes the nearest place still
free instead of disappearing. Where the fit was already good it does exactly what
rounding did, so nothing else in the corpus moves.

On the curled page it was costing three cells: 81 found, 78 placed, three landed on a
taken place and vanished - and the places they should have filled were its top left
corner, which is exactly where the sheet lifts. The surface fitted through the cells
to follow the bend had no measurement at all over the one corner that needed one. It
straightened the rest beautifully, scored 0.46 against the 0.35 needed, and produced
a picture whose corner cells were grid rules rather than digits.

With the cells kept, the same surface scores 0.73 and the page reads. Its twenty-eight
printed digits are read perfectly and its cells now export as clean digits, so it
earns its place in training rather than poisoning it: with the corner broken, adding
this page took corpus printed accuracy from 1.0000 to 0.9969 and handwriting from
0.9985 to 0.9934, every new misread predicting a 1, which is what a vertical rule
looks like to a classifier. With it fixed, printed stays at 1.0000 and handwriting
rises to 0.9987 over a corpus grown to 1405 digits.

Two regressions were caught on the way in, both of the kind that would have gone
unnoticed. The surface was allowed to win whenever it scored higher, and on one
newsprint page it scored 0.54, won, and read worse - because the score asks whether
twenty lines are present, which is the right question for "is this a grid" and a poor
one for "is this straight enough to cut into cells". The plane is now taken wherever
it clears the bar and the surface only where it does not. That was still not enough,
and the deeper fault was structural: the cells were a last resort within one working
size but not across them, so that page took a mediocre cell answer at a thousand
pixels and returned before ever reaching the outline at sixteen hundred that scores
0.52. Every size now gets its chance at an outline before any of them falls back to
the cells.

The corpus is twenty-four photographs, twenty-three of which read. The collision
ceiling goes from 109 to 125, and the sixteen are this page's own.


THE CURLED PAGE: WHAT IT ACTUALLY IS, AND WHY THE FIX FOR IT IS NOT HERE.
No change to what the app does. A measurement, an instrument to repeat it, and a
correction to something this file said last round.

Last round said that page was beyond reach because no single plane fits its own cells,
and called that a fact about the sheet of paper. The measurement was right and the
conclusion was not - a plane is not the only thing that can straighten a picture - so
this is worth setting down properly rather than leaving as a dead end.

What the average could not say, the map could. The mean residual of the best flat grid
tells you how badly it fits and never why, and it cannot tell a bending page from two
or three cells measured badly. Printed as a nine by nine, the answer is immediate. The
curled page runs 2 pixels at one corner of the grid to 44 at the other in a clean
gradient with no cell out of step with its neighbours; the other two newsprint pages
sit at 0 to 5 with three isolated spikes. Curvature is smooth. A broken contour is a
spike. That is now a test - see LatticeResidualDumpTest - because it is the question
worth asking first of any page that will not straighten, and it takes a minute.

The map also counts the lattice places no cell reached, and on the curled page that is
the sharper finding: all eighty-one of its cells are found, and three of them land on
places already taken, which leaves its top left corner - exactly where the sheet lifts
- with nothing in it. So the thing to fix there is how a cell is assigned its place in
the nine by nine, not the straightening.

A curved straightening was built and measured and is not on main; it sits on the
branch curved-page-surface, with the numbers in its commit message. Briefly: a quartic
surface through the cells' own corners takes that page from 0.00 to 0.46 against the
0.35 it needs, and the straightened picture is square over most of its area. It is not
merged because it does not make the page readable and it makes the model worse. With
the page in the corpus, the cells exported from that empty corner are grid rules rather
than digits, labelled with the digits that should have been there; trained on, corpus
printed accuracy falls from 1.0000 to 0.9969 and handwriting from 0.9985 to 0.9934, and
every new misread predicts a 1, which is what a vertical rule looks like to a
classifier. A page whose cells do not extract cannot be a training page.

The page therefore stays out of the corpus for now, and the honest summary of it is:
found is not read, and what stands between this one and a reading is first its empty
corner and then the print/handwriting collision - neither of which is the curvature
that got the blame.


A PUZZLE WHOSE BORDER IS PRINTED INTO THE PAGE BESIDE IT NOW SCANS.
One photograph, and a change to what the locator is allowed to look for.

Every way the app had of finding a grid started from the grid's outline. On newsprint
the outline is often not there to start from: this page has its right-hand rule
printed hard against the black bar the paper runs down the edge of the puzzle, and its
left-hand one against the column of horoscope text, and the threshold welds all three
into a single shape 898 pixels wide that is neither square nor the grid. The app
reported no grid, on a photograph a person reads without effort - which was Tony's
point, and it was the right one.

The cells are the way in. A border can be lost to whatever is printed next to it; a
cell cannot, because it is a hole inside the grid, and eighty-one holes of much the
same size packed together is a signature nothing else on a newspaper page has. So when
nothing traced round the puzzle scores, the cells are found instead, grouped so that a
second puzzle on the same page becomes a second group, and the corners are solved from
where those cells are.

Solving them is the part that took the work. Fitting four corners to the outside of
the cell cloud is the obvious thing and it does not work: four extreme points are four
measurements, and one cell whose contour broke moves one of them and shears the whole
rectification. Polygon approximation, the smallest enclosing rectangle and the widest
inscribed quadrilateral were each tried and each scored 0.00, and the straightened
pictures they produced were plainly askew. Eighty-one cells are eighty-one
measurements of one projection: the homography fitted to all of them takes this page
from 0.00 to 0.40 through the same scorer everything else faces, and its thirty-one
printed digits then read perfectly.

Two things that sound like improvements made it worse and are recorded so they are not
tried again. Fitting the homography by RANSAC rather than least squares took another
of these photographs from 0.48 to 0.00 - RANSAC keeps whichever subset agrees most
tightly, and on a lattice that is a handful of cells in one corner. Dropping the cells
that fit worst did the same, for a related reason: the cells furthest off the lattice
are where the page bends, which is the part of the shape the corners most need.

One of the three photographs that had no grid is still refused, and it is worth saying
why rather than leaving it open. Its page is curved enough that no single plane fits
its own cells - the best homography leaves them 9.7 pixels out on average and 30 at
worst, on a cell 65 pixels across. That is a fact about the sheet of paper, not about
the search, and a flat-page method is not going to reach it.

The page joins the corpus with a machine-verified label, which puts the collision
ceiling up from 94 cells to 109. All fifteen are its own: no cell on any page that
sorted correctly before sorts wrongly now. A page from this reader costs about fifteen
cells there whatever else changes.


A PAGE FILLED IN AT THE SIZE OF ITS PRINT NOW SCANS, AND SO DOES ONE PHOTOGRAPHED
FURTHER BACK.
Three faults, all of them found by photographs from another reader.

The first is the one that mattered. Finding the printed digits first rests on the
print being the one population on the page with a single size, and on handwriting
being bigger - which it is on every page Tony has sent, and is not on any of the ten
that arrived from someone who writes at the size of the print. Their answers were
taken for givens, the puzzle came out with seventy-odd clues, and the page would not
read at all. A page that returns more printed digits than any sudoku has is now
sorted again by ink rather than by size: print is toner, laid down heavy and even
and standing out from the paper, where a pen is none of those. It is the finer
measure and the riskier one, so it runs only where the cheap one has already failed.
That took the fault from 220 wrong cells to 77, and to 94 as more such pages
arrived, without costing a cell on any page that already read.

The second was a number chosen rather than measured. The gate refused any grid under
700 pixels; measured by shrinking every labelled photograph until its grid is a given
size and reading it again, printed digits are perfect down to 360 and handwriting to
440. Two photographs refused at 692 and 681 pixels read perfectly once let through.
The limit is 550 now, still well clear of anything that failed.

The third was a grid the app could see and would not take. The outline traced round
it sat a shade inside the grid's own printed rule, and since a grid is judged on the
weakest of the twenty lines it must have, the two outer rules falling outside that
outline took the score to nothing. When everything else has failed, the best few
outlines are now tried a couple of percent larger before giving up. One photograph
that reported no grid now reads.

The collection is twenty-two photographs and 1,701 labelled cells: newsprint,
ballpoint, creases, a sudoku on a laptop screen, one page finished wrongly, and one
still in progress. Handwriting held out photograph by photograph is 612 of 650.

THE NOTES YOU WERE SENT WERE ABOUT THE WRONG BUILD, AND THE READER HAS BEEN RETRAINED.
Seventeen consecutive builds carried the same paragraph about a corpus photograph,
because `docs/release-notes.txt` is written by hand and nothing failed when it was
not rewritten. Each build now generates its own first two lines - the version, and
the subject of the commit it was built from - and puts them above whatever prose is
there, so that at least those are true when the rest has gone stale.

The reader was retrained on the two pages added since: the one whose pencil marks
wrap onto a second line, and a sudoku photographed off a laptop screen, which is
the first grid in the corpus that is not on paper. Held out photograph by
photograph, handwriting reads 174 of 180 squares, against single runs of 168 to
172 out of 175 on the smaller corpus before it - the same, in other words, within
the wobble between runs. Two pages was never going to move a rate; what they buy
is that the next change is measured against a corpus that has a screen and a
thicket of pencil in it.

Also in this round: an erased digit is no longer read as an answer you wrote; the
shutter no longer fires by itself on framing that is about to be refused;
photographs the reader could not make a puzzle of are kept, where before only the
ones the grid detector rejected were; there is a Send diagnostics entry in the
overflow menu; the app works in landscape and keeps your scan when you turn the
phone; and the shading in the Hint button is drawn inside the button rather than
on its touch target, which is why it used to stand proud of the outline.

A SECOND PUZZLE JOINS THE CORPUS, AND IT TESTS THE PART THAT JUST CHANGED.
The one barely started, thick with pencil candidates - over half its empty squares
carry them, many written small and high in a corner where they most resemble an
answer. Every one of its 729 cells is now sorted correctly into print,
handwriting, pencil or nothing, and its twenty-three printed digits and two
answers all read right.

That matters because the ink threshold was lowered yesterday to catch the faintest
answers on your finished grid, and lowering it is exactly the change that could
have started reading pencil dust as digits. On a page covered in pencil, it does
not.

The other puzzle you sent is deliberately not in there. Read by eye it produced
eleven answers disagreeing with the solution of its own printed givens, which
means the reading was wrong rather than the paper - eleven mistakes on one grid is
not what happens. A label that is wrong is worse than no label: it teaches the
recogniser something false and every later measurement inherits it.

The full history is in docs/changelog.md.

---

YOUR FINISHED GRID IS IN THE TRAINING SET, AND TWO THINGS CAME OUT OF IT.
FIRST, THE PAPER IS RIGHT. All eighty-one squares agree with the one solution the
printed givens allow, so the three the app disagreed with were its mistakes and
not yours.

THE FAINTEST ANSWERS WERE BEING READ AS EMPTY SQUARES. Not misread - not read at
all. A pencil stroke laid lightly over a rubbed-out candidate mark did not stand
far enough below the paper around it to count as ink. That threshold has never
been tested against a finished grid before, because until now there was no
finished grid to test it against. Swept against every cell in the corpus it now
sorts all 648, where before it missed two.

AND THE MODEL HAS BEEN RETRAINED. Your grid brings fifty-seven handwritten digits
where the whole corpus held ninety-four, and it is the only completed puzzle in
it.

Here the honest number is smaller than it looks. Scored the only way that means
anything - a model that has never seen the photograph it is marking - handwriting
reads 145 of 151, or 96.0%, against 95.7% before. One photograph does not move
that much, and anybody quoting the in-training figure of 100% would be quoting
the model's memory rather than its judgement.

So: nothing on this grid should now be read as empty, and the digits themselves
are as good as 96% and no better. On eighty-one handwritten squares that is still
about three you will have to correct. The way that number comes down is more
finished grids.

The full history is in docs/changelog.md.

---

THE PHOTOGRAPHS YOU SENT FOUND IT. BOTH OF THEM NOW READ.
The app was refusing captures that plainly had a grid in them, and sending one
was what finally reproduced it - the same photograph, through the same check
here, refused for the same reason. It could not be reproduced from screenshots,
which is why this took so long.

WHAT IT WAS. Finding the grid works on a shrunk copy of the photograph - a
thousand pixels along its longest edge - because that is fast and plenty when the
puzzle fills the frame. Your captures have the puzzle taking about a sixth of a
very large picture, so by the time it had been shrunk the grid was three hundred
pixels across and its lines a couple of pixels wide. The border broke into pieces
that no longer went round anything, and the outline of the puzzle simply was not
in the list of shapes to consider. Not too small to see, and not too faint - too
shrunk before anyone looked.

It now tries a second, larger size when the first finds nothing. Both your
photographs read at that size, and so does every photograph that already worked.
Going larger still is not free: a puzzle that fills the frame stops being found at
three thousand pixels, because its lines thicken until the border merges with the
printing inside it. So it climbs, cheapest first, and stops as soon as it has an
answer.

AND SHAPES ARE NO LONGER THROWN AWAY BEFORE BEING JUDGED. Candidates were being
discarded for looking too ragged or too oblong, which sounds harmless: on one of
your captures the grid's own outline was among the discarded, and the survivor
covered half the puzzle. Deciding which shape is a grid is what the scoring is
for. The outline drawn on screen is still checked for looking like a puzzle,
because that one is a claim to you about what the app can see.

Every photograph and screenshot you have sent now reads. So does the whole test
set.

The full history is in docs/changelog.md.

---

WHY EVERY UPDATE HAS BEEN WIPING YOUR PUZZLES.
The release was signed with the debug key. A build machine has no debug key, so
one is made fresh for every build - and two builds in a row were signed by two
different certificates:

  0.1.60   SHA-256 0e467993f0438d83b3ab971bfa3e5f3c...
  0.1.61   SHA-256 8bd7c03cb97d31de9e13c743997c0b22...

Android will not update an app whose signature has changed. So every version has
arrived as an uninstall and a fresh install, taking the puzzle history, the kept
photographs and the settings with it. Not a storage bug: nothing was ever wrong
with where any of it was saved.

The build now signs with a proper key when one is provided, and says loudly when
it is not. Providing it needs four repository secrets and a keystore, which only
the owner of the project can make - docs/signing.md has the two commands.

Until then this build behaves as before. Afterwards there is one last uninstall,
because the new signature differs from whatever is on the phone now; every
version after that is an ordinary update and your puzzles stay where they are.

The full history is in docs/changelog.md.

---

TWO FAULTS, ONE OF THEM MINE FROM AN HOUR AGO.
THE PHOTO WAS NOT ALWAYS BEING KEPT, AND THE APP SAID IT WAS. The message after a
refused scan claimed the photograph had been saved whether or not the saving had
worked, and the folder it used lives on removable storage that can simply not be
there. It now writes to private storage when the external folder is missing, and
says plainly when it could not keep the photo at all.

AND THE LIST NEVER REFRESHED. The refused photos were read once, when the drawer
was first built, and the drawer stays built between openings - so a scan that
failed afterwards never appeared, which looks exactly like nothing having been
saved. The list is now looked up when the drawer opens, the same way your puzzles
are. The message also pointed at a menu item that had been moved into the puzzle
list an hour earlier.

THE SIZE FLOOR GOES BACK. A shape smaller than eight per cent of the frame is
ignored again, as it was in every version anyone reported as working. It was
dropped to three per cent, then to five, on the strength of a synthetic scene
that later measurements showed to be a poor model - and neither number was ever
shown to find a grid that eight per cent misses.

What stays is what the reported screenshot argues for. On it, the scoring change
lifts the grid from 0.33 to 0.38, over the line where the old scoring left it
just under; and the old detector offers a sliver a thousand pixels long and
three hundred wide, which is the shape you saw drawn across your puzzle.

The full history is in docs/changelog.md.

---

EVERY PHOTO IS IN YOUR PUZZLE LIST NOW, WITH A SEND BUTTON BESIDE IT.
Each saved puzzle has one, and it sends the straightened photograph - which is
exactly what the recogniser read, so it is the useful one when the digits come
out wrong.

Underneath them, a "Would not read" section: the photos the app refused, which
never became puzzles and so were invisible until now. Same thumbnail, the reason
it was turned down, send and delete. Six are kept, oldest dropped.

The separate menu item is gone. One list of everything the camera produced is
easier to find than a share buried in a menu, and there is no longer a photograph
in the app that cannot be looked at.

The app still has no internet permission and makes no network calls. It hands
the one file you pick to the app you pick, read-only; that app does the sending.

The full history is in docs/changelog.md.

---

YOU CAN NOW SEND A SCAN FROM THE PHONE.
The app keeps the last six photographs it took, and there is a "Send last scans"
item in the menu when it has any. Pick whatever you send things with; the photos
go along as attachments.

Both kinds are kept now, not just refusals. A photograph the app would not scan
explains why a scan will not start; one it scanned happily explains why the
digits came out wrong - and by the time anyone notices the digits are wrong the
photograph is long gone.

The app still has no internet permission and still makes no network calls. It
hands the file to the app you choose, read-only and one file at a time, and that
app does the sending. Nothing else it holds - your puzzles, their photographs -
is shared by this.

The full history is in docs/changelog.md.

---

WHEN A SCAN IS REFUSED, THE APP NOW KEEPS THE PHOTOGRAPH.
The scan that would not get past the shutter has been chased through every
measurement available here, and the photograph itself is not the problem: put
through the very check that rejected it, at nine different sizes, it passes every
time. Score 0.55, grid found, cells cut cleanly. So whatever the phone
photographed is not what has been examined here - and a screenshot cannot settle
that, because it is a picture of a screen showing a cropped and re-encoded copy
of the frame, nor can a photograph taken with a different camera app.

So a refused photograph is now kept, in the app's own folder, named for the
reason it was refused, and the message says which file it is. Nothing is sent
anywhere - the app still has no internet permission - it is simply there to be
looked at. Twelve are kept, oldest dropped first.

Also here: every screenshot sent back from the phone is now re-read on every test
run, so a reported failure stays reported.

AND ONE THING TAKEN BACK OUT. A phone's camera frame often carries less contrast
than a photograph decoded from a JPEG, and the checks here subtract fixed
amounts, so stretching each frame to the full range before measuring looked like
an obvious improvement. Measured, it is worth nothing at all - a page at
video range scores 0.61 where the same page at full range scores 0.60, because
these checks compare each pixel with its own neighbourhood rather than with any
fixed level. It also cost two cases out of fourteen on a cluttered table. So it
is gone, and this note is here instead.

The full history is in docs/changelog.md.

---

IT IS THE SAME READER. IT WAS NOT BEING GIVEN THE SAME PICTURE.
I said a captured photograph gets a better reader than the live preview. That was
wrong, and worth correcting: StructuralGate calls exactly the same GridLocator
the live advisor does. What differs is the picture handed to it - a full-size,
focused, still exposure against a small preview frame taken mid-movement - and
the rules applied afterwards, which are in fact stricter for a capture, not
looser.

Which leaves one real question: if the finder is the same, why give it the worse
picture? Partly there is no choice, since analysing at full resolution would take
seconds a frame. But the app was asking for 960x720 and telling the camera to
fall back to the next size *down* when that exact mode was unavailable - so on
many phones it was quietly working from 640x480, or less, and never saying so. It
now asks for 1280x960 and falls back upward. That costs almost nothing, because
the detector reduces whatever it is given to a fixed working size before looking
for shapes.

And the message when no grid is found no longer reads like a refusal. The shutter
is live whatever it says, and a photograph gets judged again at full size, so a
live frame the app cannot make sense of is a reason to suggest something - never
a reason to stop you taking the picture.

The full history is in docs/changelog.md.

---

SCANNING: THE READER NO LONGER OFFERS SHAPES THAT ARE NOT SHAPES.
WHAT THE SCREENSHOTS SHOWED. An outline slicing diagonally across the grid, and
another spanning the whole screen. Neither is a mistake about where to draw - a
contour that is not four-sided was being reduced to its four extreme points,
which for a wandering blob of pattern is a sliver that means nothing. And the
last build had lowered the size at which a shape is considered at all, so far
more of those blobs got in, and the ten largest shapes in the picture could all
be tablecloth while the puzzle waited outside the list.

A candidate now has to be square enough, upright enough and solid enough to be a
sudoku before it is considered - questions that cost nothing to ask and do not
depend on what else is in the picture. When a contour is not four-sided, the
smallest rectangle around it stands in, rather than its extreme points.

The size floor goes back most of the way: it was 0.08, went to 0.03 on the
strength of a synthetic scene that later measurements showed was a poor model of
a real table, and is now 0.05.

WHAT IS NOT THE CAUSE, measured rather than assumed: the resolution the camera
analyses at, camera shake, the viewfinder cropping the frame, and - which
surprised me - how much pencil is on the page. A grid buried in annotations
scores exactly what a clean one does.

If a puzzle still will not scan, press the shutter anyway. The button is always
live, and a captured photograph is examined at full resolution by a far better
reader than the live one. If what comes back is wrong, that photograph is the
most useful thing you can send.

The full history is in docs/changelog.md.

---

SCANNING: THE APP NOW SHOWS YOU WHAT IT CAN SEE, AND SEES FAR MORE.
WHY A WELL-FRAMED PUZZLE WAS REFUSED. Not the wording, and not the aim. A grid
is scored on the weakest of the twenty lines it must have, and each line was
measured against the *strongest* line in the picture - which is the outer border,
drawn two or three times the width of the inner ones. That asks every thin line
to be as inky as the thick one, and it is a bar that rises the further away the
puzzle is, until a perfectly framed grid scores 0.32 against a pass mark of 0.35
for no reason except its distance from the camera.

Lines are now measured by how far they stand above the page around them, and each
is judged against a typical line rather than the boldest one. A photograph the
app had always refused went from 0.15 to 0.49. The whole corpus passes, which it
did not before.

Two more things came out of the same measurements. Nothing smaller than eight per
cent of the frame was even considered as a possible grid, and the viewfinder crops
the frame - so a puzzle filling your screen is a good deal smaller in the picture
the app is given. That floor is now three per cent. And every candidate shape used
to be blown up to 1152 pixels square before being scored; they are scored at their
own size, and only the winner is enlarged, which made the whole pass faster rather
than slower.

AND IT SHOWS YOU WHAT IT HAS FOUND. There is now an outline drawn over the
preview: green when it is a grid the app accepts, amber when it is the closest
thing it could find. The amber case is the one that matters - it shows the reader
fastening onto the page, or the book, or the edge of the table, which you can do
something about at once and which no message could ever have told you.

The full history is in docs/changelog.md.

---

THE CAMERA NOW SAYS WHAT IS WRONG WITH THE PUZZLE IN FRONT OF IT.
"Point the camera at a sudoku puzzle" was the answer to every failure, including
a puzzle filling the frame - which is advice to do the thing you are already
doing. It was also the only answer possible, because the checks for light and
glare sat on the far side of the branch: they ran once a grid had been found, so
in the one case where the picture was too dark or too shiny to find a grid at
all, nothing checked for either.

Those now run first, on the frame as it is. Beyond them the reader says what it
honestly knows: whether it saw anything square-cornered in the shot at all, and
how close the best of them came to reading as nine rows and nine columns. So a
frame with nothing in it still asks for a puzzle, a dark one says it is dark, a
shiny one says to tilt the page, and a square that is not a grid says that - and
says to fill the frame with the grid alone, straight on and flat.

Not a guess about focus: nothing in the app measures blur against a calibrated
threshold, and a message that fires when it should not is worse than one line
fewer.

The full history is in docs/changelog.md.

---

THE FORCING CHAIN NOW SHOWS WHERE THE MISSING DIGIT COULD HAVE GONE.
"WHY CAN'T 5 BE IN r1c2?" A whole box in red and a sentence saying 5 has nowhere
left to go is a claim, not a picture. It never said where 5 could have gone, so
it could not show that every one of those places had just been taken by one of
the arrows - and the one square that was marked looked like the only one that
mattered.

Every square in the dead end that could have held the digit is now marked with
it, and they all carry the number of the final step. Each of them is a square one
of the arrows has just taken, which is the part of the argument that was missing
from the grid. The explanation says so too.

The marked digit is no longer struck through. It sits in a square tinted red,
inside a unit tinted red; a third mark saying the same thing only crowded the
pencil marks underneath. A dead end that is a single square keeps its cross,
because there the point is that no digit at all fits, and there is nothing to
write.

And the tutor's top lines are closer together, because the room they take comes
straight out of the explanation below them.

The full history is in docs/changelog.md.

---

TEN MORE TECHNIQUES, AND THE TUTOR'S MENU NOW LISTS ONLY WHAT IS ACTUALLY THERE.
TWENTY-THREE INSTEAD OF THIRTEEN. Added: naked and hidden quads, jellyfish,
skyscraper, two-string kite, W-wing, remote pairs, simple colouring, unique
rectangle and the XY-chain. All of them are in Strategies with the rule and how
to hunt for it, as the others are.

What they buy is shorter explanations, not more solvable puzzles - the forcing
chain could already eliminate anything they can, given enough length. A named
four-square pattern in place of an eleven-square trail is the whole point. On the
hardest puzzle in the test set the new ones take eleven steps that were chains
before; on ordinary puzzles they take many more, because the patterns they
recognise are the ones ordinary puzzles are built from.

ONLY WHAT IS THERE. The tutor's menu listed every technique with "none" beside
most of them - two dozen things you could not choose, with the handful you could
lost among them. It now lists what applies to the puzzle in front of you and
nothing else. The full list, including everything that does not apply, is under
Strategies, which is the page for reading.

Every one of these is audited: the soundness sweep checks each technique against
the known answer at every position the solver passes through, and now runs over
4,400 deductions. The five whose patterns neither test puzzle happens to contain
have hand-built positions of their own, because a technique that never fires is
untested code claiming to be a lesson.

The list is still not all of sudoku, and never will be.

The full history is in docs/changelog.md.

---

THE TUTOR KEEPS YOUR PLACE, THE HINT SHOWS HOW FAR IT WILL GO, AND THERE IS A
choice about how the route is built.

WHERE YOU LEFT OFF. Closing the tutor forgot the position, so a glance at the
grid halfway through a sixty-step route cost the whole route. Closing is not
finishing.

A CHOICE ABOUT FORCING CHAINS, in Settings, because it is a real trade and not a
better and a worse. The first chain the app finds is the one on the square with
the fewest candidates, which says nothing about how long the argument from it
runs - on the hardest puzzle tested, that came out as a fifteen-square trail.
Weighing twenty and walking the shortest keeps every chain to about ten squares
instead, but weaker eliminations mean more of them: 110 steps against 87. Short
chains is the default. Neither is wrong; they are different kinds of work.

THE HINT BUTTON FILLS IN QUARTERS. A hint has four treads - the box, the
technique, the square, the digit - and the button gave no sign of that, so a
press that revealed the next one looked like a press that had done nothing.

The "how" that opens a technique's how-to now sits at the end of the sentence it
belongs to rather than on a line of its own. The digit that cannot be placed is
drawn as an outline, so the photograph underneath still shows through what it is
being compared with, and the dead end is numbered like every other square on the
trail. The panel can no longer be left half open. And the route picker says how
long the route is even while you are browsing one technique - it was showing the
length of whatever was being browsed.

The full history is in docs/changelog.md.

---

THE TUTOR OPENS ON STEP 0 - ITSELF - AND THE PANEL NOW JUST FOLLOWS YOUR FINGER.
STEP 0 IS THE INTRODUCTION. What the route ahead asks of you used to share a
screen with the first step, so opening the tutor gave you a paragraph about the
whole puzzle and a paragraph about one square at once, and dragging the panel up
changed which you were reading halfway through the movement. It is a position of
its own now: 0 of 60, drawing nothing on the grid, with the route running from 1.
Browsing a single technique gets its own opening too - what the rule is, and how
many places it applies from here.

THE PANEL FOLLOWS THE FINGER. There were two things fighting over its height: an
animation that started when the tutor opened, and a separate offset the drag
wrote to. Opening began the animation, the animation reached the top while the
finger was still down, and letting go handed control back to a value that had
long since arrived - so the panel jumped, and the whole opening played again.
There is one value now. The drag writes it directly, and an animation settles it
afterwards, once.

The full history is in docs/changelog.md.

---

TWO ARROWS LEAVING ONE SQUARE NO LONGER LOOK LIKE ONE ARROW PASSING THROUGH IT.
A square that forces two others in opposite directions was drawing both down the
same line, tail to tail, which reads as a single long arrow with a head at each
end - and once you read it that way, nothing in the picture leads back to where
the chain began.

Each arrow is now set a little to one side of the line between the two squares.
The offset is taken from the arrow's own direction, so two arrows pointing
opposite ways land on opposite sides of the square they leave and separate,
instead of doubling up.

The full history is in docs/changelog.md.

---

THE CHAIN'S TRAIL IS NUMBERED, ITS DEAD END NAMES THE DIGIT, AND THE TUTOR NO
longer changes what it says as you let go of it.

NUMBERED, BECAUSE ARROWS ALONE CANNOT BE FOLLOWED. Reported from the phone: not
every arrow could be traced back to the square the chain assumes. They all can -
there is now a test that walks every arrow backwards and fails if any of them
wanders off or goes round in a circle - but a dozen crossing arrows cannot be
followed by eye, which amounts to the same thing. Each square on the trail now
carries its place in the order, and every square's number is larger than its
parent's, so counting up always walks away from the assumption.

WHAT CANNOT BE PLACED, AND WHERE. The red band showed where the trouble was
without saying what it was; the sentence underneath was carrying that on its
own. The digit with nowhere left to go is now drawn in the band, in red, with a
line through it.

NOTHING CHANGES WHEN YOU LET GO. The panel showed the route's opening remark
while being dragged and the first step once released, so the words changed under
you exactly as the movement ended - and flickered doing it. The tutor now opens
on the first millimetre of the drag rather than at the end of it, so what you
pull into view is what stays there, grid and all. The opening remark also comes
before the first step now instead of after it, which is the order you read them
in.

The full history is in docs/changelog.md.

---

EVERY FORCING CHAIN ON THE HARDEST PUZZLE NOW COMES WITH ITS TRAIL DRAWN.
WHY THAT DIGIT IS OUT. The chain that could not be drawn fell back to
highlighting one square and asserting that following it through the grid led
somewhere impossible - which is what you were being shown most of the time, and
is not an explanation. The trace was weaker than the propagation that proves the
step: fixing a square throws away the other digits it was holding, and each of
those may have been one of the last places its digit could go. Following those
too takes the hardest puzzle in the test set from six chains in eleven with a
picture to eleven in eleven.

The square the chain assumes something about is now ringed, so the eye knows
where a trail of a dozen arrows begins, and the square at the end is crossed
out, because the point of that square is that nothing goes in it - a red tint
alone reads as "wrong answer here", which is the opposite of what it means.

ONE LINE INSTEAD OF THREE. The technique was named twice, once beside the route
picker and once in the key beside the colour of its own squares. The key is the
one that earns it, so the picker, the key and how far through the run you are
now share a line. The stepping controls are centred, where a thumb lands.

The question mark has gone. The how-to opens from a "how" at the end of the
step it belongs to, with a chevron that turns down as it expands - beside the
stepping controls it looked like help with the controls. A thread of a scrollbar
shows when there is more text below.

Tapping the tutor's handle when it is open now shuts it, as well as opening it
when it is shut.

The full history is in docs/changelog.md.

---

THE TUTOR IS ONE PANEL NOW, NOT A BAND THAT SUMMONS A SHEET.
Dragging the band used to move the band; the panel arrived afterwards, separately,
which is not the same gesture at all. It is now a single panel whose height
changes: what you drag is the thing that grows, and letting go settles it to
whichever end it is nearer. Tapping it opens it too.

Resting, it shows its handle and the title Tutor - nothing else. The arrow and
the step count have gone; everything below the title is laid out as usual and
simply clipped away, so the open panel is the same panel rather than a second
one wearing its clothes. Nothing under the title is even built while it is shut,
since none of it can be seen and all of it costs a solve.

The full history is in docs/changelog.md.

---

A FORCING CHAIN NOW SHOWS ITS WORKING.
It was the one technique that asked you to take its word for it. "Suppose this
square were 3. Following that through the grid leaves some square with no digit
at all" - and then a single highlighted square, which is not an argument, it is
an assertion. The chain was computed and thrown away.

It is kept now, and drawn. Every square the assumption forces is tinted and
carries the digit it is forced to hold, with an arrow from whatever forced it.
The wall at the end is red: either one square left with nothing it can hold, or
a whole unit with nowhere left to put some digit, and the text says which.

The trail is a tree, not a line. The first version drew the single path back
from whatever hit the wall, which looked like an argument and was not one - the
wall leans on side branches too, and replaying only that path did not reach it.
What is drawn now is every placement the conclusion actually rests on and
nothing else. There is a test that replays each trail square by square and fails
if the wall it claims is not there.

Six of the eleven chains on the hardest puzzle in the test set come with a
picture, of five to nine squares. The rest are still made - the full propagation
refutes more than a trail can show - just without one. An argument of more than
a dozen squares is not drawn at all: fifteen arrows over a photograph of a page
is a scribble, not an explanation.

The full history is in docs/changelog.md.

---

THE TUTOR NOW RESTS AT THE FOOT OF THE SCREEN INSTEAD OF HIDING BEHIND A BUTTON.
A LIP YOU PULL UP. The tutor sits at the very bottom as the same panel it will
become, collapsed and labelled, with the four layer buttons above it. Drag it up,
flick it, or tap it. What it is is legible before you touch it, and the gesture
that opens it is the one that closes it again.

IT NO LONGER RESIZES. Open, it takes the whole area under the photograph and
keeps it, whatever the step in front of you happens to say. It used to be as tall
as its contents, so every step moved everything on screen.

TWO LINES BACK. "How to spot one" had a line to itself; it is now a question mark
at the end of the header, costing no lines at all. And "Put 5 in row 2, column 6"
is gone: the digit is drawn in its square with a ring round it, so the sentence
was telling you where to look at the thing you were already looking at. The line
that says an elimination fills nothing in stays, because that one is describing
something you cannot see - a step where the board does not change at all.

New photo is now New sudoku.

The full history is in docs/changelog.md.

---

THE TUTOR IS A SHEET YOU PULL UP AND PUSH AWAY, AND THE MENU IS SORTED OUT.
THE TUTOR HAS A DOOR. It used to be a row of buttons that was always there and
could only be left by pressing Next until the route ran out - sixty presses on a
hard puzzle. It is now a sheet: swipe it down to leave, swipe sideways to step,
and it covers the buttons while it is up because that is what the screen is for.
The photograph above it never moves.

A PROGRESS LINE YOU CAN AIM AT. Sixty steps drawn one mark each comes out finer
than a fingertip and says nothing about what the marks are. The line now shows
one block per run of the same technique - usually about a dozen - each as wide
as the run is long, and tapping one jumps there. The shape of the puzzle is in
it: a wide block is a long grind of one technique, a narrow one is a move that
only worked once. Under it, which technique you are in and how far through.
Chevrons either side of the counter land on one step exactly, since swiping is
quick but coarse.

FIVE BUTTONS, ONE ROW. Read, Check, Hint, Solve, Tutor - the order you would use
them in. The tutor used to need a row of its own even when it was not running,
and both rows applied their own system-bar inset, leaving a band of dead screen
between them.

SIX DESTINATIONS, THREE KINDS OF THING. The drawer was holding all of them. It
now answers only the question it is for - which puzzle - with New photo at the
top and your puzzles below. Strategies, Settings and About are the app talking
about itself: they are read once and then rarely, so they sit behind one
overflow icon instead of two icons in the bar and a row in the drawer. The
camera keeps its own icon, being the thing you press most.

The full history is in docs/changelog.md.

---

BACK NAVIGATES INSTEAD OF CLOSING THE APP, ANSWERING A SQUARE SHOWS SOMETHING,
and a naked single no longer claims things that are not true.

BACK IS A STACK. It used to be one press from the door on almost every screen.
It now undoes the last thing that appeared - the history drawer, then a screen
you opened, then a layer over the puzzle, then the puzzle itself - and only
leaves from the camera with nothing behind it. Written as an actual stack rather
than a rule per screen, because "back from the puzzle shows the camera" and
"back from the camera shows the puzzle" are each sensible alone and together are
a loop with no way out of the app at all.

"WHY IS IT NOT A 1?" A fair question with no answer on the screen. A naked
single said "every other digit already appears in this row, column or box" and
highlighted six squares. Six squares can only account for six digits, and the
sentence was false: the missing two had been struck out by earlier steps, which
place nothing and so leave nothing on the board to point at. It now says what
the highlight actually accounts for, names the digits that went earlier, and
says which step took them: "4 and 6 were ruled out of this square earlier, not
by anything you can see around it. Press Back to see how: 4 at step 13 and 6 at
step 14." Nine of the eighty-seven steps on the hardest puzzle in the test set
were making the false claim.

A DIGIT YOU TYPE IS NOW DRAWN. It is on no photograph, so the square used to
look exactly as empty after answering it as before - unless the answer was
wrong, in which case a line appeared saying so. Backwards on both counts. Your
answer is now drawn the way the reading layer draws handwriting, on that square
only, and nothing grades it. Whether it is right is what the Check button is
for. The one exception is a finished grid, where there is nothing left to work
on and "not yet" is the whole news.

ROOM TO READ. The pane under the grid was two lines tall and everything went in
it. "46 cells to go" is now a small 46/81 in the corner under the grid where it
belongs. The technique's name has moved into the key, beside the colour of the
squares it is talking about, so it is not printed twice. The long "how to spot
one" folds behind a control instead of burying the reasoning for the step in
front of you. The route's opening remark is said once at step one rather than
under every step. And the two button rows each applied their own system-bar
inset, which left a bar of dead screen between them.

Read, Check, Hint, Solve, in the order you would use them, moved to the foot of
the screen. New photo is now in the menu above your puzzles as well as in the
bar.

The full history is in docs/changelog.md.

---

THE TUTOR NOW FINISHES ANY PUZZLE IT CAN READ.

Two things were needed.

A THIRTEENTH TECHNIQUE: the forcing chain. Every other one recognises a pattern.
This one does not - it assumes a digit, follows the consequences across the grid,
and if some square is left with no digit it can hold, the assumption was wrong.
It is the technique of last resort and the honest name for what people actually
do at that point. It carried eleven of the eighty-seven steps on the hardest
puzzle in the test set.

AND A ROUTE THAT DOES NOT STOP. When every technique comes up empty, the tutor
settles one square by trying its candidates out, says plainly that is what it
has done, and reasons on from there. It never dresses a lookup up as a
deduction, and it never leaves you halfway.

On Arto Inkala's 2012 puzzle - the one built to defeat solvers, where not one of
the thirteen can make a single move from the opening position - the tutor now
walks all 87 steps to a finished grid. 85 of them are justified by a named
technique. Two squares are tried out.

There is a test that walks every puzzle in the fixtures to a complete, legal
grid, and another that fails if more than a quarter of a route is trial rather
than reasoning. Finding the end is not enough; it has to be mostly taught.

Also fixed on the way: the route used to stop the moment the grid was solved,
which sounds right and is not. The last placement of a run often collapses
several squares to a single candidate at once, and those squares were being
filled in but never explained - six of them on the easy puzzle.

HINTS: THE SECOND PRESS DID NOTHING, and now it does.

A naked single's evidence used to be the square itself, which points at nothing
once the square is taken out - so the first two presses drew the same eight
squares and the second looked broken. The evidence is now the EIGHT NEIGHBOURS
that used the other eight digits up, which is the whole reason the square is
forced. All four presses now change what is on screen, and there is a test that
fails if any two of them ever draw the same thing again.

The wording is plainer too, and much shorter. Each press says exactly what the
next one will give you:

  1. There is a square you can fill in the highlighted box.
  2. <technique>, what it claims, and the squares that prove it.
  3. It is the ringed square.
  4. The digit, and why.

The long "how to hunt for this" paragraph is gone from hints. It is the wrong
thing to read while stuck on one square, and it is one tap away in Strategies.

THE TUTOR HAS A MENU. Tap the middle of the tutor bar and every technique is
there with the number of places it applies right now - including the ones that
apply nowhere, because "naked single, none" is worth knowing when you are
hunting for one. Pick any of them to step through its findings, or Best route to
go back to what the app would do next.

AND IT SAYS WHAT THE ROUTE IS FIRST. Six eliminations in a row read as six
unrelated facts. Now it tells you up front how many steps there are, how many of
them actually fill a square in - often none, on a hard puzzle - and that
clearing candidates out of the way is the job rather than a disappointment. Each
step then leads with what it does: "Put 7 in row 4, column 2", or "This fills
nothing in. It rules 5 out of three squares."

A NEW ICON, with meaning this time.

The old one was a three-by-three grid, which is noughts and crosses, and a nine
that read as a q. Stylish and about nothing.

This one is the thing the app looks at: a printed sudoku on paper, zoomed until
one box fills the frame, ruled lines running off the edges because the grid
carries on past them, and one cell filled in the app's cyan - which is what the
app does to a square it has just read.

Paper rather than ink, because a dark tile says nothing and white paper with
black rules says printed puzzle. Digits, because a grid without them is noughts
and crosses whatever else you do to it. One box rather than all nine, because a
whole nine-by-nine turns to unreadable mush below about seventy pixels - which I
checked by rendering it at 36, 48, 72, 96 and 192 rather than guessing.

The corner cells are left empty on purpose. An adaptive icon is only guaranteed
to show the middle circle of itself, and the corners of a three-by-three sit
exactly where a round launcher mask bites. Nothing is put there that would be
missed.

A NEW ICON, and one that actually shows up.

The mark is the app's own visual language: a nine cut out of a cyan cell, with
the three-by-three grid ruled across it and the digit crossing the lines -
exactly what the reading layer does to a photograph.

It was invisible in App Tester for a concrete reason. The icon existed only as
an adaptive XML, which Android renders but anything reading the APK from outside
cannot. There are now real PNGs at all five densities alongside it, verified by
pulling them back out of the built APK rather than trusting the source folder.

Also fixed: on Android 12 and up the system draws its own launch screen using
the icon's FOREGROUND on the window background. The foreground is the dark nine,
so that was a dark nine on a dark ground - an invisible splash. The launch screen
is now the icon's cyan, and the window itself is the app's ink rather than the
platform's grey, so there is no flash of the wrong colour on the way in.

THE TUTOR NOW KNOWS TWELVE TECHNIQUES, up from four.

New: naked pair, hidden pair, naked triple, hidden triple, X-wing, Y-wing,
XYZ-wing and swordfish - alongside the naked and hidden singles, pointing pairs
and box line reductions it already had. Each carries the same two things: the
rule in one sentence, and how to go hunting for it while holding a pencil.

START TUTOR. The walkthrough no longer greets you with a paragraph the moment a
scan finishes. There is a button, and pressing it starts the tutor.

BROWSE ANY TECHNIQUE. Open Strategies from the menu and every technique says how
many places it applies in the puzzle you have open right now. Tap it and you
step through them one at a time on the photo, exactly like the tutor's own route
- except these are alternatives rather than a sequence, so the board does not
fill in as you go. Seeing the same pattern four times in one grid is what turns
a definition into something you can spot.

A word on trust: twelve techniques are too many to check by eye, so there is now
a test that audits every deduction all twelve can make, at every position the
solver passes through, against the known answer - 2972 of them. It caught a real
bug in the swordfish, which was eliminating a digit that belonged where it sat.

And an honest limit: none of the twelve can touch the very hardest puzzles.
Inkala's 2012 puzzle defeats all of them from the first move. Those need chains
and colouring, which this app cannot yet explain, and it will tell you so rather
than pretend.

A TRAINING APP, NOT JUST A SOLVER.

HINTS NOW COME IN STEPS. Press Hint and it shows you the box to look in, and
nothing else. Press it again and it names the technique and tells you how to
spot that kind of move. Again and it rings the square. Only the fourth press
gives you the digit. Most of the time you will have what you needed before the
bottom - and a hint you stop early is a move you made yourself.

WALK ME THROUGH IT. The whole route from where you are to the answer, one human
step at a time, with Back and Next. Each step names its technique, explains why
it works here, and tells you how to find that kind of step yourself next time.
The board fills in as you go, so every move is seen from the position it was
made in.

It starts from YOUR position, not from the printed digits - answers you already
got right are taken as read, so it never walks you through work you have done.
Answers you got wrong are left out rather than believed, because a route built
on a wrong digit leads somewhere wrong.

STRATEGIES, in the menu. The four ways of reasoning this app knows, what each
one claims, and how to go looking for it while holding a pencil. Two of them
place nothing at all by themselves - they clear candidates so that something
else becomes obvious, which is the thing that is hardest to learn from a solver.

WHAT THIS PUZZLE ASKS OF YOU, said before you start: how many steps are left,
and the hardest technique among them. That last part is the useful one - it is
the reason a puzzle feels stuck, and it names what to go and read.

When the app runs out of techniques it says so plainly rather than pretending.
Its four do not finish the hardest puzzles, and telling you that is more use
than a solution you cannot follow.

THE BUTTONS NO LONGER MOVE. Hint, Check, Solve and Read are pinned above the
system buttons, and everything whose height depends on what is being said now
scrolls in its own space above them. A banner appearing used to push them down
the screen, out from under the thumb about to press one.

FIXED: the cell editor's last button landed underneath the system navigation
buttons. The sheet insets its top but not its bottom.

FIXED: telling the app a square is empty left it coloured in the Read layer as
whatever it had been read as. The reader's account of a square is now discarded
the moment you overrule it, and the editor says "You cleared this square"
instead of claiming to have read it that way.

The tint is a little stronger, so the knocked-out digits read more clearly.

FIXED: the square the app was unsure about was marked with a hairline at the
bottom of one square out of eighty-one, which is not something anyone is going
to find. On a flagged square the bar is now three times as thick.

FIXED: that bar could come out GREEN on the very square you were being asked
about. A square can be flagged with the classifier perfectly confident - the
solver threw the digit out because a clump of pencil marks was not a printed
digit at all, which the classifier had no way to know. Its confidence was then
beside the point, and green read as reassurance. A flagged square is never green
now.

The banner also says WHY, which it knew all along and was throwing away:
"One cell looked like a printed digit but is not."

FIXED: no way out of the history list. The drawer sheet is 360dp wide by
default, which is wider than this phone's whole screen, so it covered the scrim
completely and there was nothing outside it to tap. It is now 84% of the width,
it has a close button of its own, and the system back button closes it.

Back now works everywhere else too: from About it goes to Settings, from
Settings back to your puzzle or the camera. Before, it left the app.

UX polish, all of it from your list.

THE GRID NO LONGER MOVES. Its size now comes from the window and nothing else. It
used to take whatever the controls left over, so switching layer resized it and
the square under your finger moved. The controls scroll in their own space now.

WHAT WAS READ is a fourth button beside Hint, Check and Solve. The squares stay
FILLED, as you preferred, and the digit is now a HOLE CUT IN THE FILL - it adds
no ink at all, and what shows through the glyph is the page itself.

It is BIG AND CENTRED, the size the solution draws its digits. A small hole in a
light tint is a small amount of contrast, and the way to buy contrast without
covering anything is to make the hole bigger rather than the tint heavier.

Centring it over your own digit turns out to be the point rather than the
problem. Where the reading is right the two coincide and the square just reads
as a clean digit standing out of a tinted surround. Where it is wrong, your
strokes come out of the glyph and into the tint - far more visible than two
small digits sitting side by side ever were.

The tint is heavier than before at forty-four percent, which it can afford to be
now that most of it is cut away again on any square holding a digit. The
confidence bar is a hairline along the bottom edge.

The same treatment is now used on a red square in Check, because it is the same
statement: this is what I saw there.

THE AMBER RING IS GONE. Its bottom edge sat straight across the confidence bar -
two marks for one fact, and the coarser of the two was hiding the finer. The bar
carries it now: green above nine tenths, amber above six, red below. A square
the app is unsure about can never come out green, so the colour alone tells you
which ones it would ask about. In the other layers those squares still show
their bar, so they are still findable.

NEW PHOTO moved to the top bar as a camera icon, next to settings.

THE TITLE IS GONE. In its place is a menu button that slides your puzzles in over
whatever you are looking at, and the app name and version sit beside it.

ABOUT, PRIVACY AND THE LICENCES have their own screen, reached from settings.
They are reading, not settings, and burying three screens of prose under two
switches made both harder to find.

DELETING A PUZZLE now asks first. It throws away the photo as well as the grid
and there is no undo.

Also: the system bars are pinned dark, so on a phone in light mode the clock and
back arrow no longer disappear into the black.

A rebuilt interface, and a recogniser that no longer confuses pencil with print.

THE INTERFACE

Every screen is laid out again from scratch. The camera preview now fills the screen
the way a camera should, with four corner brackets marking where the puzzle goes -
the square preview was putting the image under the notch and leaving half the screen
black. Everything is inset from the system bars.

The puzzle screen is a fixed three-part column: bar, photo, controls. No scrolling to
reach a button, and nothing wraps one letter per line any more. Tapping a cell opens
a proper sheet with a 3x3 keypad instead of a row of buttons at the bottom of a page.

The colours now mean one thing each, and every mode shows a key naming what is drawn:

  - One fill per cell, so no two colours ever mix. The orange you saw was red for
    "wrong" sitting on top of yellow for "unsure", and it meant nothing.
  - Doubt is now a RING around the cell rather than a fill, so it can sit over any
    colour without inventing a new one.
  - What the app read is drawn on a small chip, so it reads as the app talking rather
    than as a digit on your paper.

NEW: WHAT WAS READ

A fourth layer, under the three help buttons: the recogniser's own account of the
page. Every square is tinted by what it was taken to be - printed, handwritten,
pencil marks, or empty - with the digit read and a bar showing how sure it was. Tap a
square for the exact figures and the runner-up guess.

HINTS

A hint now reasons from the givens plus the answers you have already got right, so it
points at a cell you have not filled. That is why it told you a square could only be
a 9 when you had already written 9 in it: it was reasoning from the printed digits
alone and rediscovering your own work.

There is no "Show me the digit" button any more. You were right that it was
redundant - every explanation names the digit in passing.

THE RECOGNISER

Your instinct about finding the print first was right, and this build takes it
further: printed digits share a font, a size AND AN INK. That third one is what was
missing. On your annotated puzzle the pencil marks are numerous and uniform enough
that seventeen of them agree on size more tightly than the print does - so size alone
picked the marks. Print is toner and everything else is pencil, and preferring the
darkest uniform group settles it on every test photo, first try.

The printed digits then define everything else. Measured across all seven photos,
print sits within 5% of its own median height, handwriting is always at least 15%
taller, and the large pencil marks that reach digit size give themselves away by
sitting high in the cell - which is where you write candidates. Every ink blob in the
test set is now sorted correctly.

Also fixed: when the printed digits did not make a solvable puzzle, the app used to
try changing one until they did. On your photo it changed a correctly read 1 into a 7
and reported success. It now tries REMOVING the least print-like digit first, which
is the mistake that actually happens.

Handwriting recognition: your 1 is written with a long flag, and your 9 with a curled
tail. MNIST - the standard training set, collected in America - has almost neither,
which is why eight of nine of your ones were read as 4. The model is now also trained
on digits drawn in the continental style, and on the corpus itself.

SMALLER THINGS

The overlay is now drawn on the grid lines the app actually found in your photo,
rather than by dividing the picture into ninths. Paper is not flat, and on a
bowed page the two are several pixels apart at the edges - which mattered as
soon as every square got a tint.

The hint digit used to be amber, which is also the colour of the app's own
uncertainty. A hint is the solution for one square, so it is now the same blue
as the solution, and amber means one thing only.

Solve draws over a dim scrim, so the answer stays readable on squares you have
already written in.

Please keep sending photos of anything it gets wrong. Every one of these fixes came
from a specific photo that failed.
