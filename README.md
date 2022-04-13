# Word counter

## How to run

Set the timewindow in `wordcount.timeWindow` in `application.conf`.

Set the executable path and arguments in `eventProcessor.command` and 
`eventProcessor.args` in `application.conf`.

Call `sbt run`, and check http://localhost:8080/words.

## TODO

Add tests for EventProcessor