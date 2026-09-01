# Changelog

Every round of changes, newest first. This file is for reading; it is not what gets
uploaded to App Distribution.

`docs/release-notes.txt` carries the notes for the CURRENT release only, and is capped
by Firebase at 16,384 characters. It used to hold all of this, growing every round, and
one day it went over the limit and the upload failed after a full CI build had already
run. Keep that file short and put the history here.

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
