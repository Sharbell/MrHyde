<!doctype html>
<html>
    <head>
    <meta charset="utf-8">
    <title>MrHyde - a Jekyll scratchpad</title>
    <link rel="stylesheet" href="../static/layout.css" type="text/css">
    <link href='http://fonts.googleapis.com/css?family=Droid+Sans:400,700' rel='stylesheet' type='text/css'>
    </head>

    <body bgcolor="white" text="black">
        <p><strong>{{header}}</strong></p>
        <table>
        %for row in rows:
            <tr><td>{{row}}</td></tr>
        %end
        </table>
        <p></p>
        <div align="center"><img src="ic_background.svg" alt="MrHyde logo"></div>
        <p></p>
        <footer style="text-align: center;">&copy; FauDroids 2015</footer>
    </body>
</html>
