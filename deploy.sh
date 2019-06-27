#!/bin/bash

# porcelain=$(git status --porcelain)

# if [ -n "$porcelain" ]
# then
#     echo "PLEASE COMMIT YOUR CHANGE FIRST!!!"
#     exit 1
# fi

lein run
cp -r public/firemore-docs/* ~/firemore-docs
cd ~/firemore-docs
git add .
git commit -m "AUTO COMMIT"
git push
