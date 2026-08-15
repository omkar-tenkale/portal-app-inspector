Files to text to clipboard
```
find . -type f -not -path '*/.*' | while read -r file; do echo "$file"; echo "---"; cat "$file"; echo -e "\n"; done | pbcopy
```