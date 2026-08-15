RECURSIVE COMMAND (Includes all subdirectories)
--------------------------------------------------
find "$PWD" -type f -not -path '*/.*' | while read -r file; do echo "### File: $file"; echo '```'; cat "$file"; echo -e '\n```\n'; done | pbcopy


NON-RECURSIVE COMMAND (Current directory only)
--------------------------------------------------
find "$PWD" -maxdepth 1 -type f -not -path '*/.*' | while read -r file; do echo "### File: $file"; echo '```'; cat "$file"; echo -e '\n```\n'; done | pbcopy
