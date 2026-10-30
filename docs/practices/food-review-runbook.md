# Weekly food review

Foods people add from photos of a pack wait in the admin console's review
queue until someone checks them. In v1 nothing reads them with AI on the
server: the phone reads the label, and a person decides. This is the weekly
pass, 5 to 10 minutes for a normal week.

## The pass

1. **Open the queue.** `https://opson-server-prod.fly.dev/admin/` › Review.
   Pending is oldest first.
2. **Bulk approve the clean ones.** "Approve N that passed every check" takes
   the foods whose phone reading found every headline number in the photo's
   text, with every check passing (the rule is `ReviewBundle.eligible` in
   `apps/admin`). The confirm lists them. Skim the names, then type the
   environment's name to go ahead.
3. **Export the rest.** "Export pending (JSON)" downloads
   `opson-review-prod-<date>.json`. Leave it in `~/Downloads`, outside the repo.
4. **Download the photos.** The photo URLs need the admin token, which should
   not go to an AI tool. Paste this from the repo root. It asks for the token
   without showing it, then for the bundle's path:

   ```bash
   stty -echo; printf "Admin token (prod): "; read -r ADMIN_TOKEN; stty echo; printf "\n"
   printf "Bundle path (drag the file in): "; read -r BUNDLE; BUNDLE="${BUNDLE/#\~/$HOME}"; BUNDLE="${BUNDLE%% }"
   if [ -n "$ADMIN_TOKEN" ] && [ -f "$BUNDLE" ]; then
     ADMIN_TOKEN="$ADMIN_TOKEN" python3 -I scripts/review_bundle.py photos "$BUNDLE"
   else
     echo "Need a token and an existing bundle file. Nothing downloaded."
   fi
   unset ADMIN_TOKEN BUNDLE
   ```

   The script sends the token only to the server the bundle's `env` names
   (Dev or Prod), over https, for a submission's own photo path, and never
   follows a redirect. A photo it won't fetch is listed with the reason; a
   bundle someone edited to point elsewhere gets nothing.

5. **Pre-check with your own AI tool.** Open Claude Code (your subscription,
   no API key) and hand it [the prompt](../../scripts/prompts/weekly-food-review.md)
   with the bundle's path. It fills in a decision per food and writes
   `<bundle>-decided.json`. The privacy policy says the reviewer's AI
   assistant runs with model training off, so check once that your Claude
   account's "Help improve Claude" setting (claude.ai › Settings › Privacy) is
   off before the first pass, and use Claude, the one assistant the policy names.
6. **Import and skim.** Review › "Import decisions", pick the decided file.
   The preview shows each decision, any corrected numbers as before → after,
   and anything wrong with a row (an id that isn't pending any more, a reject
   with no reason). Untick what you disagree with, then "Apply N" and confirm.
7. **Finish by hand.** Whatever is still pending (skips, unticked rows) goes
   fast with the keyboard: `j` and `k` move, `a` approves, `r` jumps to the
   reject reason, `s` saves corrections, `t` shows the OCR text, `Esc` closes,
   `?` lists them. After a decision the next one opens.
8. **Delete the photos.** Remove the bundle and its `-photos` folder from
   `~/Downloads`. They hold people's photos.

## Things to know

- **Photos are deleted 30 days after a decision**, on the server, as the
  privacy policy promises. A bundle you keep would outlive that, so delete it.
- **Nothing here calls an AI API from the server.** The export is a file. The
  pre-check runs on your machine, with a tool you pick, and its decisions do
  nothing until you confirm them in the console.
- **The AI's decisions are suggestions.** Fake data and copied data rejects end
  the submitter's chance of Trusted for good, so the apply confirm says so when
  the file has any.
- **More than 200 waiting?** One export holds the oldest 200. Apply them, then
  export again.
- The console's Review section in [apps/admin/README.md](../../apps/admin/README.md)
  describes every control.
