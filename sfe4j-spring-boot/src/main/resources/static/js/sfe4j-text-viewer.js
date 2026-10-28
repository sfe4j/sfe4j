/*
 * Text page of SFE4J: line numbers, encoding and read mode switching, follow mode, search and copy.
 *
 * The file content is never treated as markup. Every piece of text reaches the page through
 * textContent or a text node, so a file containing scripts, event handlers or SSE separators is
 * displayed as characters and nothing else.
 *
 * The script is intentionally dependency free and lives in its own file; the vendored Bootstrap,
 * jQuery and DataTables files are not modified.
 */
(function () {
    "use strict";

    var MAX_LINES = 5000;
    var MAX_CHARS = 2 * 1024 * 1024;

    var frame = document.getElementById("sfe4j-frame");
    if (!frame) {
        return;
    }

    var content = document.getElementById("sfe4j-content");
    var statusLabel = document.getElementById("sfe4j-status");
    var charsetSelect = document.getElementById("sfe4j-charset");
    var modeSelect = document.getElementById("sfe4j-mode");
    var linesInput = document.getElementById("sfe4j-lines");
    var newlinesSelect = document.getElementById("sfe4j-newlines");
    var lineNumbersToggle = document.getElementById("sfe4j-line-numbers");
    var searchInput = document.getElementById("sfe4j-search");
    var reloadButton = document.getElementById("sfe4j-reload");
    var copyButton = document.getElementById("sfe4j-copy");
    var followButton = document.getElementById("sfe4j-follow");
    var autoScrollToggle = document.getElementById("sfe4j-autoscroll");
    var fileInfo = document.getElementById("sfe4j-file-info");

    var logsEnabled = frame.getAttribute("data-logs-enabled") === "true";
    var apiBase = frame.getAttribute("data-api") || "";
    var filePath = frame.getAttribute("data-file") || "";

    var state = {
        text: content ? content.textContent : "",
        cursor: frame.getAttribute("data-cursor") || "",
        source: null,
        following: false,
        stickToBottom: true
    };

    function setStatus(message, kind) {
        if (!statusLabel) {
            return;
        }
        statusLabel.textContent = message || "";
        statusLabel.className = "label label-" + (kind || "default");
        statusLabel.style.display = message ? "" : "none";
    }

    function charsetValue() {
        return charsetSelect ? charsetSelect.value : "";
    }

    function modeValue() {
        return modeSelect ? modeSelect.value : "head";
    }

    function linesValue() {
        var value = linesInput ? parseInt(linesInput.value, 10) : 0;
        return isNaN(value) || value <= 0 ? 200 : value;
    }

    function normalized(text) {
        if (newlinesSelect && newlinesSelect.value === "lf") {
            return text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
        }
        return text;
    }

    function splitLines(text) {
        return text.length === 0 ? [] : text.split(/\r\n|\n|\r/);
    }

    function enforceCaps() {
        var lines = splitLines(state.text);
        var dropped = false;
        while (lines.length > MAX_LINES || state.text.length > MAX_CHARS) {
            if (lines.length <= 1) {
                break;
            }
            var removed = lines.shift();
            state.text = state.text.substring(removed.length + 1);
            dropped = true;
        }
        if (dropped) {
            setStatus("The oldest lines were dropped: the page keeps at most "
                    + MAX_LINES + " lines and " + (MAX_CHARS / (1024 * 1024)) + " MiB.", "warning");
        }
        return lines;
    }

    function appendHighlighted(fragment, line, query) {
        var haystack = line.toLowerCase();
        var from = 0;
        var index = haystack.indexOf(query);
        if (index < 0) {
            fragment.appendChild(document.createTextNode(line));
            return;
        }
        while (index >= 0) {
            if (index > from) {
                fragment.appendChild(document.createTextNode(line.substring(from, index)));
            }
            var mark = document.createElement("mark");
            mark.textContent = line.substring(index, index + query.length);
            fragment.appendChild(mark);
            from = index + query.length;
            index = haystack.indexOf(query, from);
        }
        if (from < line.length) {
            fragment.appendChild(document.createTextNode(line.substring(from)));
        }
    }

    /*
     * One block per logical line. The number and the text share the row, so a line that wraps
     * pushes its own row down instead of shifting every number below it, which is what happened
     * while the numbers and the text were two independent columns.
     */
    function render() {
        var lines = enforceCaps();
        var text = normalized(state.text);
        var displayLines = splitLines(text);
        var query = searchInput && searchInput.value ? searchInput.value.toLowerCase() : "";

        if (content) {
            var fragment = document.createDocumentFragment();
            for (var i = 0; i < displayLines.length; i++) {
                var row = document.createElement("div");
                row.className = "sfe4j-line";

                var number = document.createElement("span");
                number.className = "sfe4j-no";
                number.setAttribute("aria-hidden", "true");
                number.textContent = String(i + 1);

                var cell = document.createElement("span");
                cell.className = "sfe4j-txt";
                if (query) {
                    appendHighlighted(cell, displayLines[i], query);
                } else {
                    cell.appendChild(document.createTextNode(displayLines[i]));
                }

                row.appendChild(number);
                row.appendChild(cell);
                fragment.appendChild(row);
            }
            content.textContent = "";
            content.appendChild(fragment);
        }

        if (lineNumbersToggle) {
            if (lineNumbersToggle.checked) {
                content.className = "sfe4j-content";
            } else {
                content.className = "sfe4j-content sfe4j-no-numbers";
            }
        }

        if (fileInfo && lines.length > 0) {
            fileInfo.setAttribute("data-lines", String(lines.length));
        }
    }

    function scrollToBottom() {
        if (!frame || !state.stickToBottom) {
            return;
        }
        frame.scrollTop = frame.scrollHeight;
    }

    function updateFollowButton() {
        if (!followButton) {
            return;
        }
        followButton.textContent = state.following ? "Pause" : "Follow";
        followButton.setAttribute("aria-pressed", state.following ? "true" : "false");
        followButton.className = state.following ? "btn btn-warning btn-sm" : "btn btn-primary btn-sm";
    }

    function stopFollow() {
        if (state.source) {
            state.source.close();
            state.source = null;
        }
        state.following = false;
        updateFollowButton();
    }

    function startFollow() {
        if (!logsEnabled || !window.EventSource) {
            setStatus("Following is not available.", "warning");
            return;
        }

        var url = apiBase + "/follow?file=" + encodeURIComponent(filePath)
                + "&charset=" + encodeURIComponent(charsetValue())
                + "&lines=" + encodeURIComponent(String(linesValue()));
        if (state.cursor) {
            url += "&cursor=" + encodeURIComponent(state.cursor);
        }

        var source = new EventSource(url);
        state.source = source;
        state.following = true;
        updateFollowButton();
        setStatus("Following the file.", "info");

        source.addEventListener("snapshot", function (event) {
            var data = parse(event.data);
            if (!data) {
                return;
            }
            state.text = data.text || "";
            state.cursor = data.cursor || "";
            render();
            scrollToBottom();
            setStatus("Following the file.", "info");
        });

        source.addEventListener("append", function (event) {
            var data = parse(event.data);
            if (!data) {
                return;
            }
            state.text += data.text || "";
            state.cursor = data.cursor || "";
            render();
            scrollToBottom();
        });

        source.addEventListener("reset", function (event) {
            var data = parse(event.data);
            setStatus("The file was rotated or truncated, reading it again: "
                    + (data && data.reason ? data.reason : "reset"), "warning");
        });

        source.addEventListener("gap", function (event) {
            var data = parse(event.data);
            setStatus("Some lines are missing, a new snapshot is read: "
                    + (data && data.reason ? data.reason : "gap"), "warning");
        });

        source.addEventListener("error", function (event) {
            if (typeof event.data === "string") {
                var data = parse(event.data);
                stopFollow();
                setStatus((data && data.code ? data.code : "ERROR")
                        + (data && data.message ? ": " + data.message : ""), "danger");
                return;
            }
            stopFollow();
            setStatus("The connection was closed.", "warning");
        });
    }

    function parse(raw) {
        try {
            return JSON.parse(raw);
        } catch (error) {
            return null;
        }
    }

    function reload() {
        if (!logsEnabled) {
            setStatus("Reloading requires sfe4j.logs.enabled=true.", "warning");
            return;
        }
        var url = apiBase + "/text?file=" + encodeURIComponent(filePath)
                + "&mode=" + encodeURIComponent(modeValue())
                + "&lines=" + encodeURIComponent(String(linesValue()))
                + "&charset=" + encodeURIComponent(charsetValue());

        fetch(url, {headers: {Accept: "application/json"}, credentials: "same-origin"}).then(function (response) {
            if (!response.ok) {
                return response.json().then(function (body) {
                    throw new Error((body && body.code ? body.code : "ERROR")
                            + (body && body.message ? ": " + body.message : ""));
                }, function () {
                    throw new Error("Request failed with status " + response.status);
                });
            }
            return response.json();
        }).then(function (data) {
            state.text = data.text || "";
            state.cursor = data.cursor || "";
            render();
            scrollToBottom();
            setStatus(data.truncated ? "Only part of the file is shown." : "Up to date.", "info");
        }).catch(function (error) {
            setStatus(String(error.message || error), "danger");
        });
    }

    function copy() {
        var text = normalized(state.text);
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                setStatus("The loaded text was copied.", "success");
            }, function () {
                setStatus("The text could not be copied.", "warning");
            });
            return;
        }
        var area = document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "readonly");
        area.style.position = "fixed";
        area.style.top = "-1000px";
        document.body.appendChild(area);
        area.select();
        var copied = false;
        try {
            copied = document.execCommand("copy");
        } catch (error) {
            copied = false;
        }
        document.body.removeChild(area);
        setStatus(copied ? "The loaded text was copied." : "The text could not be copied.",
                copied ? "success" : "warning");
    }

    if (reloadButton) {
        reloadButton.addEventListener("click", reload);
    }
    if (copyButton) {
        copyButton.addEventListener("click", copy);
    }
    if (followButton) {
        followButton.addEventListener("click", function () {
            if (state.following) {
                stopFollow();
                setStatus("Paused. The position is kept until you follow again.", "info");
            } else {
                startFollow();
            }
        });
    }
    if (searchInput) {
        searchInput.addEventListener("input", render);
    }
    if (lineNumbersToggle) {
        lineNumbersToggle.addEventListener("change", render);
    }
    if (newlinesSelect) {
        newlinesSelect.addEventListener("change", render);
    }
    if (autoScrollToggle) {
        state.stickToBottom = autoScrollToggle.checked;
        autoScrollToggle.addEventListener("change", function () {
            state.stickToBottom = autoScrollToggle.checked;
        });
    }
    if (frame) {
        frame.addEventListener("scroll", function () {
            var atBottom = frame.scrollTop + frame.clientHeight >= frame.scrollHeight - 8;
            state.stickToBottom = atBottom;
            if (autoScrollToggle && autoScrollToggle.checked !== atBottom) {
                autoScrollToggle.checked = atBottom;
            }
        });
    }

    render();
    updateFollowButton();
})();
