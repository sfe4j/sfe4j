<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <title>${meta.title}</title>

    <!-- Bootstrap -->
    <link href="${ctx}/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <!-- HTML5 shim and Respond.js for IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
    <script src="https://oss.maxcdn.com/html5shiv/3.7.3/html5shiv.min.js"></script>
    <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->
</head>

<body>
<div class="container" role="main">
    <div class="page-header">
        <span class="pull-right">
            <ul class="list-inline">
            <#list meta.quickLinks as title, dir>
                <li>
                    <a href="?dir=${dir}" data-toggle="tooltip" data-placement="top" title="Jump to ${dir}"
                       data-original-title="Jump to ${dir}">${title}</a>
                </li>
            </#list>
            </ul>
        </span>
        <h3>${meta.title}</h3>
        <p>${meta.description}</p>
    </div>

    <#assign currentDirectoryFullName = fileTree.currentDirectory.fullName >
    <ol class="breadcrumb">
        $
        <#if currentDirectoryFullName == '/'>
            /
        <#else>
            <#list currentDirectoryFullName?split("/") as x>
                <#if x!=''>
                    <#assign currentFullFolder = currentDirectoryFullName?substring(0, currentDirectoryFullName?index_of(x)) + x>
                    <#if currentDirectoryFullName?index_of(x) != 0>/</#if>
                    <a href="?dir=${currentFullFolder}"
                       data-toggle="tooltip" data-placement="top" title="View '${currentFullFolder}' Folder">${x}</a>
                </#if>
            </#list>
        </#if>
    </ol>

    <div class="row">
        <div class="col-md-12">
            <div class="panel panel-default">
                <table class="table table-hover">
                    <thead>
                    <tr>
                        <th class="col-md-6">Name</th>
                        <th>Permission</th>
                        <th>Size&nbsp;</th>
                        <th>Date Modified</th>
                        <th>Actions</th>
                    </tr>
                    </thead>

                    <#if fileTree.parentDirectory??>
                        <#assign parentDirectory = fileTree.parentDirectory >
                        <tr>
                            <td>
                                <span class="glyphicon glyphicon-arrow-up"></span>
                                <#if parentDirectory.readable>
                                    <a href="?dir=${parentDirectory.fullName}"
                                       data-toggle="tooltip" data-placement="right" title="View Parent Directory">Parent
                                        Directory</a>
                                <#else>
                                    Parent Directory
                                </#if>
                            </td>
                            <td>${parentDirectory.accessAttributes}</td>
                            <td></td>
                            <td></td>
                            <td></td>
                        </tr>
                    </#if>

                    <#assign childDirectories = fileTree.childDirectories >
                    <#list childDirectories as childDirectory>
                        <tr>
                            <td>
                                <span class="glyphicon glyphicon-folder-close"></span>
                                <#if childDirectory.readable>
                                    <a href="?dir=${childDirectory.fullName}"
                                       data-toggle="tooltip" data-placement="right"
                                       title="View '${childDirectory.name}' Folder">${childDirectory.name}</a>
                                <#else>
                                    ${childDirectory.name}
                                </#if>
                            </td>
                            <td>${childDirectory.accessAttributes}</td>
                            <td></td>
                            <td>${childDirectory.dateTime}</td>
                            <td></td>
                        </tr>
                    </#list>

                    <#assign files = fileTree.files >
                    <#list files as file>
                        <tr>
                            <td>
                                <span class="glyphicon glyphicon-file"></span>
                                <#if file.readable && (file.name?ends_with(".txt") || file.name?ends_with(".log") || file.name?ends_with(".html") || file.name?ends_with(".htm"))>
                                    <a href="${ctx}/file-viewer?file=${file.fullName}"
                                       data-toggle="tooltip" data-placement="right"
                                       title="View '${file.name}'">${file.name}</a>
                                <#else>
                                    ${file.name}
                                </#if>
                            </td>
                            <td>${file.accessAttributes}</td>
                            <td>${file.size}</td>
                            <td>${file.dateTime}</td>
                            <td>
                                <#if file.readable>
                                    <a class="btn btn-xs btn-info" href="${ctx}/file-downloader?file=${file.fullName}"
                                       target="_blank"
                                       title="Download '${file.name}'" data-toggle="tooltip" data-placement="right"
                                       data-original-title="Download '${file.name}'">Download</a>
                                </#if>
                            </td>
                        </tr>
                    </#list>
                </table>
            </div>
        </div>
    </div>

    <footer class="footer">
        <p>♥ Lovely made by <a href="https://github.com/guuuuo" target="_blank">N_G</a></p>
    </footer>

</div> <!-- /container -->

<!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
<script src="${ctx}/js/jquery.min.js"></script>
<!-- Include all compiled plugins (below), or include individual files as needed -->
<script src="${ctx}/bootstrap/js/bootstrap.min.js"></script>

<script type="text/javascript">
    $(document).ready(function () {
        $('[data-toggle="tooltip"]').tooltip();
    });
</script>
</body>
</html>