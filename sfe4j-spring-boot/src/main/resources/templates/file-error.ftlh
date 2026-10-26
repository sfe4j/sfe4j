<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${status} ${code}</title>

    <link href="${ctx}/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <style>
        .sfe4j-request-id {
            color: #6c757d;
            font-size: 90%;
            font-family: monospace;
        }
    </style>
</head>

<body>
<div class="container" role="main">
    <div class="page-header">
        <h3>${status} · ${code}</h3>
    </div>

    <div class="alert alert-danger">${message}</div>

    <p class="sfe4j-request-id">Request ID: ${requestId}</p>

    <p>
        <#if backLink??>
            <a class="btn btn-primary" href="${backLink}">${backLabel}</a>
        </#if>
        <a class="btn btn-default" href="${ctx}/file-explorer">Back to the file explorer</a>
    </p>
</div>
</body>
</html>
