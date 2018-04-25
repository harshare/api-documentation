#!/bin/bash

sbt clean coverage test it:test coverageOff coverageReport
python dependencyReport.py api-documentation
