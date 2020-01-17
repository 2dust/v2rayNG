git config --global core.autocrlf true
git pull origin master
git add -A
git commit -m "v2.0.1"
git push origin master
git tag -a v2.0.1 -m "release v2.0.1"
git push origin --tags
pause