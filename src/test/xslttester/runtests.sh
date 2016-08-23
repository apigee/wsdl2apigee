#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# Created: <Mon Aug  1 07:55:50 2016>
# Last Updated: <2016-August-01 08:56:22>
#

#path_to_saxon=~/dev/java/lib/saxon-9.1.0.8.jar
#path_to_saxon=~/dev/java/lib/saxon9he.jar
path_to_saxon=""
xsl=generator.xsl
test_outputs=./test_outputs
warnings=recover

usage() {
  local CMD=`basename $0`
  echo "$CMD: "
  echo "  Runs an XSL against a set of inputs and compares outputs."
  echo "  Uses the xmllint utility."
  echo "usage: "
  echo "  $CMD [options] "
  echo "options: "
  echo "  -S /path/to/saxon.jar   the path to the saxon JAR."
  echo "  -O /dir/for/outputs     the path to a directory to hold outputs. Default: ${test_outputs}"
  echo "  -T xsl                  the XSL sheet to run. Default: ${xsl}"
  echo "  -W                      make Saxon silent about reoverable warnings."
  echo
  echo
  exit 1
}

clear_directory() {
    local dir="$1"
    rm -fr "${dir}"
    if [ ! -d "${dir}" ]; then
        mkdir ${dir}
    fi
}

xml_infoset_diff() {
    local file1="$1" file2="$2"
    local file1_out=${file1/.xml/.c14n.xml}
    local file1_out=${file1_out/tests/${test_outputs}}
    
    local file2_out=${file2/.xml/.c14n.xml}
    local file2_out=${file2_out/tests/${test_outputs}}
    
    local file1_out_formatted=${file1_out/.c14n/.c14n.formatted.xml}
    local file2_out_formatted=${file2_out/.c14n/.c14n.formatted.xml}
    
    xmllint --exc-c14n "$file1" > ${file1_out}
    xmllint --format "${file1_out}" > ${file1_out_formatted}

    xmllint --exc-c14n "$file2" > ${file2_out}
    xmllint --format "${file2_out}" > ${file2_out_formatted}
    
    echo diff "${file1_out_formatted}" "${file2_out_formatted}"
    diff "${file1_out_formatted}" "${file2_out_formatted}"
}

## =======================================================

echo
echo "This script creates a set of API Products, Developers, and developer apps enabled on those"
echo "products. Emits the client id and secret for each."
echo "=============================================================================="

while getopts "hS:T:O:W" opt; do
  case $opt in
    h) usage ;;
    S) path_to_saxon=$OPTARG ;;
    T) xsl=$OPTARG ;;
    O) test_outputs=$OPTARG ;;
    W) warnings="silent" ;;
    *) echo "unknown arg" && usage ;;
  esac
done

[ ! -f "${path_to_saxon}" ] && printf "Specify a path for saxon\n" && usage

if [ ! -d "${JAVA_HOME}" ]; then
    echo "Must set JAVA_HOME in this script to point to a JDK7."
    echo
    exit 1
fi

clear_directory ${test_outputs}

for input in tests/*.input.xml ; do
    expectedoutput=${input/input/expectedoutput}
    num=${input#input.xml}
    printf "\n\n******************************************************************\n"   
    printf "running test %s\n" $num
    printf "  input: %s\n" $input
    printf "  expected output: %s\n" $expectedoutput
    actualoutput=${expectedoutput/expected/actual}
    actualoutput=${actualoutput/tests/${test_outputs}}
    # java -jar /path/to/saxon.jar xmlfile xslfile
    echo $JAVA_HOME/bin/java -jar "${path_to_saxon}" -warnings:${warnings} "${input}"  "${xsl}"
    $JAVA_HOME/bin/java -jar "${path_to_saxon}" -warnings:${warnings} "${input}"  "${xsl}" > $actualoutput
    xml_infoset_diff "${expectedoutput}"  "${actualoutput}" 
    printf "\n\n"
done

