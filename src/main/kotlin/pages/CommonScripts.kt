package pages

import com.rnett.kframe.dom.classes.Head
import com.rnett.kframe.dom.externalStylesheet
import com.rnett.kframe.dom.script
import com.rnett.kframe.dom.scriptFrom
import com.rnett.kframe.dom.stylesheet
import com.rnett.kframe.element.ElementBuilder
import com.rnett.kframe.element.Style

val commonScripts: ElementBuilder<Head> = {

    externalStylesheet("https://fonts.googleapis.com/icon?family=Material+Icons")
    externalStylesheet("/css/materialize.min.css", attrs = *arrayOf("media" to "screen,projection"))

    scriptFrom("/js/materialize.min.js")


    script { +"""${'$'}(document).ready(function(){ M.AutoInit(); });""" }

    stylesheet(".modal" to Style { boxSizingRaw = "border-box" })

    script {
        +"""${'$'}(document).ready(function(){
    M.AutoInit();
  });"""
    }

    script {
        +"""
                function fallbackCopyTextToClipboard(text) {
  var textArea = document.createElement("textarea");
  textArea.value = text;
  document.body.appendChild(textArea);
  textArea.focus();
  textArea.select();

  try {
    var successful = document.execCommand('copy');
    var msg = successful ? 'successful' : 'unsuccessful';
    console.log('Fallback: Copying text command was ' + msg);
  } catch (err) {
    console.error('Fallback: Oops, unable to copy', err);
  }

  document.body.removeChild(textArea);
}
function copyToClipboard(text) {
  if (!navigator.clipboard) {
    fallbackCopyTextToClipboard(text);
    return;
  }
  navigator.clipboard.writeText(text).then(function() {
    console.log('Async: Copying to clipboard was successful!');
  }, function(err) {
    console.error('Async: Could not copy text: ', err);
  });
}
                    """.trimIndent()
    }
}
