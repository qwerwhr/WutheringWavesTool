module cn.tealc.wwt.app.update {
    requires javafx.controls;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires org.apache.commons.codec;
    requires java.net.http;

    opens cn.tealc.wwt.app.update.model to com.fasterxml.jackson.databind;

    exports cn.tealc.wwt.app.update;
    exports cn.tealc.wwt.app.update.model;
    exports cn.tealc.wwt.app.update.task;
}