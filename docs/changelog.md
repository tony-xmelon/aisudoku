# Changelog

Every round of changes, newest first. This file is for reading; it is not what gets
uploaded to App Distribution.

`docs/release-notes.txt` carries the notes for the CURRENT release only, and is capped
by Firebase at 16,384 characters. It used to hold all of this, growing every round, and
one day it went over the limit and the upload failed after a full CI build had already
run. Keep that file short and put the history here.

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
