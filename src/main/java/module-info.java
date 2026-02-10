module com.johnreah.mapster {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.net.http;

    exports com.johnreah.mapster;
    opens com.johnreah.mapster to javafx.graphics;
}
