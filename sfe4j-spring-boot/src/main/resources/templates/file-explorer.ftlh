<#assign title = "Simple File Explorer">

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <title>${title}</title>

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
        <span class="pull-right"></span>
        <h3>${title}</h3>
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
                                <#if file.readable>
                                    <a href="#" onclick="downloadFile('${file.fullName}')"
                                       data-toggle="tooltip" data-placement="right"
                                       title="Download File '${file.name}'">${file.name}</a>
                                <#else>
                                    ${file.name}
                                </#if>
                            </td>
                            <td>${file.accessAttributes}</td>
                            <td>${file.size}</td>
                            <td>${file.dateTime}</td>
                            <td></td>
                        </tr>
                    </#list>
                </table>
            </div>
        </div>
    </div>

    <footer class="footer">
        <p>♥ Lovely made by N.G</p>
    </footer>

</div> <!-- /container -->

<form id="fileOp" name="fileOp" method="post">
    <input type="hidden" name="dir" id="dir" value="${currentDirectoryFullName}"></input>
    <input type="hidden" name="download" id="download" value=""></input>
    <input type="hidden" name="run" id="run" value=""></input>
    <input type="hidden" name="delete" id="delete" value=""></input>
</form>

<!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
<script src="${ctx}/js/jquery.min.js"></script>
<!-- Include all compiled plugins (below), or include individual files as needed -->
<script src="${ctx}/bootstrap/js/bootstrap.min.js"></script>

<script type="text/javascript">
    function downloadFile(fileName) {
        document.getElementById('download').value = fileName;
        document.getElementById('run').value = '';
        document.getElementById('delete').value = '';
        submitForm('fileOp');
    }

    function submitForm(formName) {
        document.getElementById(formName).submit();
    }

    $(document).ready(function () {
        $('[data-toggle="tooltip"]').tooltip();
    });
</script>
</body>
</html>