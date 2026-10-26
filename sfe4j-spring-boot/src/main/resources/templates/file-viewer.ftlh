<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${preview.fileName} - ${meta.title}</title>

    <link href="${ctx}/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <style>
        .sfe4j-preview {
            white-space: pre-wrap;
            word-break: break-word;
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            padding: 12px;
            max-height: 80vh;
            overflow: auto;
        }

        .sfe4j-meta {
            color: #6c757d;
            font-size: 90%;
        }
    </style>
</head>

<body>
<div class="container-fluid" role="main">
    <div class="page-header">
        <h3>${preview.fileName}</h3>
        <p class="sfe4j-meta">
            ${preview.size?c} bytes<#if preview.truncated> · only the first ${preview.maxBytes?c} bytes are shown</#if>
            · ${preview.encoding}
            · <a href="${url.listing(preview.parentPath!"")}">Back to directory</a>
        </p>
    </div>

    <#if preview.truncated>
        <div class="alert alert-warning">
            Content truncated: the file is larger than the configured preview limit, only the first
            ${preview.maxBytes?c} bytes are displayed.
        </div>
    </#if>

    <#if preview.decodingIssue>
        <div class="alert alert-warning">
            Some bytes could not be decoded as ${preview.encoding} and are displayed as replacement characters.
        </div>
    </#if>

    <pre class="sfe4j-preview">${preview.content}</pre>
</div>
</body>
</html>
