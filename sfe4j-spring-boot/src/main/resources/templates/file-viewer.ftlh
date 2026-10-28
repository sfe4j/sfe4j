<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${preview.fileName} - ${meta.title}</title>

    <link href="${ctx}/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <style>
        .sfe4j-text-frame {
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            max-height: 70vh;
            min-height: 120px;
            overflow: auto;
        }

        .sfe4j-content {
            padding: 8px 0;
            font-family: Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
            font-size: 12px;
            line-height: 1.5;
            tab-size: 4;
        }

        /* Rendered before the script runs, and by the follow snapshot path. */
        .sfe4j-plain {
            margin: 0;
            padding: 0 12px 12px 12px;
            font: inherit;
            white-space: pre-wrap;
            word-break: break-word;
        }

        /* One element per logical line, so a wrapped line keeps its own number. */
        .sfe4j-line {
            display: flex;
            align-items: stretch;
            min-height: 1.5em;
        }

        .sfe4j-no {
            position: sticky;
            left: 0;
            z-index: 1;
            flex: 0 0 auto;
            min-width: 4em;
            padding: 0 10px 0 12px;
            border-right: 1px solid #dee2e6;
            background-color: #eef1f4;
            color: #6c757d;
            text-align: right;
            user-select: none;
        }

        .sfe4j-txt {
            flex: 1 1 auto;
            min-width: 0;
            padding: 0 12px;
            white-space: pre-wrap;
            word-break: break-word;
        }

        .sfe4j-content.sfe4j-no-numbers .sfe4j-no {
            display: none;
        }

        .sfe4j-content mark {
            background-color: #ffe08a;
            padding: 0;
        }

        .sfe4j-hidden {
            display: none;
        }

        .sfe4j-meta {
            color: #6c757d;
            font-size: 90%;
        }

        .sfe4j-status {
            margin-bottom: 8px;
        }
    </style>
</head>

<body>
<div class="container-fluid" role="main">
    <div class="page-header">
        <h3>${preview.fileName}</h3>
        <p class="sfe4j-meta">
            <span id="sfe4j-file-info">${preview.size?c} bytes<#if preview.truncated> · only part of the file is shown</#if>
                · ${preview.encoding}</span>
            · <a href="${url.listing(preview.parentPath!"")}">Back to directory</a>
            · <a href="${url.download(preview.path!"")}">Download</a>
        </p>
    </div>

    <#if preview.truncated>
        <div class="alert alert-warning">
            Content truncated: the file is larger than the configured limit, only
            ${preview.maxBytes?c} bytes are displayed.
            <#if preview.lineTruncated>The last line is cut and is not shown completely.</#if>
        </div>
    </#if>

    <#if preview.decodingIssue>
        <div class="alert alert-warning">
            Some bytes could not be decoded as ${preview.encoding} and are displayed as replacement characters.
            Try another encoding.
        </div>
    </#if>

    <div class="sfe4j-status">
        <span id="sfe4j-status" class="label label-default" role="status" aria-live="polite"></span>
    </div>

    <form class="form-inline sfe4j-toolbar" onsubmit="return false;">
        <div class="form-group">
            <label for="sfe4j-charset">Encoding</label>
            <select id="sfe4j-charset" class="form-control input-sm">
                <option value="">Detect</option>
                <#list charsets![] as charset>
                    <option value="${charset}"<#if preview.encoding == charset> selected</#if>>${charset}</option>
                </#list>
            </select>
        </div>

        <#if logsEnabled!false>
            <div class="form-group">
                <label for="sfe4j-mode">Read</label>
                <select id="sfe4j-mode" class="form-control input-sm">
                    <option value="head"<#if (preview.mode!'') == "head"> selected</#if>>First lines</option>
                    <option value="tail"<#if (preview.mode!'') == "tail"> selected</#if>>Last lines</option>
                </select>
            </div>

            <div class="form-group">
                <label for="sfe4j-lines">Lines</label>
                <input id="sfe4j-lines" class="form-control input-sm" type="number" min="1"
                       max="${(maxLines!0)?c}" value="${((preview.lines > 0)?then(preview.lines, defaultLines!0))?c}">
            </div>
        </#if>

        <button id="sfe4j-reload" type="button" class="btn btn-default btn-sm">Reload</button>

        <div class="checkbox">
            <label><input id="sfe4j-line-numbers" type="checkbox" checked> Line numbers</label>
        </div>

        <div class="form-group">
            <label for="sfe4j-newlines">Newlines</label>
            <select id="sfe4j-newlines" class="form-control input-sm">
                <option value="keep">Keep</option>
                <option value="lf">Show CRLF as LF</option>
            </select>
        </div>

        <button id="sfe4j-copy" type="button" class="btn btn-default btn-sm">Copy</button>

        <#if logsEnabled!false>
            <button id="sfe4j-follow" type="button" class="btn btn-primary btn-sm" aria-pressed="false">Follow</button>
            <div class="checkbox">
                <label><input id="sfe4j-autoscroll" type="checkbox" checked> Auto scroll</label>
            </div>
        </#if>

        <div class="form-group">
            <label for="sfe4j-search">Find in loaded text</label>
            <input id="sfe4j-search" class="form-control input-sm" type="search" placeholder="plain text">
        </div>
    </form>

    <div class="sfe4j-text-frame" id="sfe4j-frame"
         data-file="${preview.path!''}"
         data-api="${ctx}/sfe4j/api"
         data-mode="${preview.mode!'head'}"
         data-charset="${preview.encoding}"
         data-lines="${preview.lines?c}"
         data-logs-enabled="${(logsEnabled!false)?c}"
         data-cursor="${preview.cursor!''}">
        <div id="sfe4j-content" class="sfe4j-content"><pre class="sfe4j-plain">${preview.content}</pre></div>
    </div>

    <p class="sfe4j-meta" id="sfe4j-hint">
        Search only looks at the text loaded in the browser, never at the whole file.
    </p>
</div>

<script src="${ctx}/js/sfe4j-text-viewer.js"></script>
</body>
</html>
