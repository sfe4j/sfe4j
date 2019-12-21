<#assign title = "Simple Tail">

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <title>${title}</title>

    <!-- Bootstrap -->
    <link href="${ctx}/assets/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <style type="text/css">
        html, body {
            height: 100%
        }

        #container {
            min-height: 100%;
            height:100%;
            padding: 5px;
        }
    </style>
</head>

<body>
<div id="container">
    <div></div>
</div>

<!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
<script src="${ctx}/assets/js/jquery.min.js"></script>
<script type="text/javascript">
    $(document).ready(function () {
        // 指定websocket路径
        var websocket = new WebSocket('${socketURL}');
        websocket.onmessage = function (event) {
            // 接收服务端的实时日志并添加到HTML页面中
            $("#container div").append(event.data);
            // 滚动条滚动到最低部
            $("body").scrollTop($("#container div").height() - $("#container").height());
        };
    });
</script>
</body>
</html>