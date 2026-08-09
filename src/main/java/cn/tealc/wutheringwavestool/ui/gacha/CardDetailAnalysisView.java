package cn.tealc.wutheringwavestool.ui.gacha;

import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import cn.tealc.wutheringwavestool.model.analysis.SsrData;
import cn.tealc.wutheringwavestool.ui.component.PoolNameCell;
import cn.tealc.wutheringwavestool.util.LocalResourcesManager;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.util.*;

public class CardDetailAnalysisView implements Initializable, FxmlView<CardDetailAnalysisViewModel> {
    @InjectViewModel
    private CardDetailAnalysisViewModel viewModel;
    @FXML
    private HBox children;
    @FXML
    private Label currentCountLabel;
    @FXML
    private StackPane chartHolder;
    @FXML
    private ComboBox<String> chartTypeCombo;
    @FXML
    private ComboBox<String> themeCombo;
    @FXML
    private ToggleSwitch showLabelsSwitch;
    @FXML
    private ListView<String> poolListview;
    @FXML
    private Label rLabel1;
    @FXML
    private Label rLabel2;
    @FXML
    private Label srLabel1;
    @FXML
    private Label srLabel2;
    @FXML
    private Label ssrLabel1;
    @FXML
    private Label ssrLabel2;
    @FXML
    private ListView<SsrData> ssrListView;
    @FXML
    private FlowPane ssrFlowPane;
    @FXML
    private Label totalCountLabel;
    @FXML
    private Label totalCostLabel;
    @FXML
    private Label ssrAvgLabel;
    @FXML
    private Label ssrMaxLabel;
    @FXML
    private Label ssrMinLabel;
    @FXML
    private Label upCountLabel1;
    @FXML
    private Label upCountLabel2;
    @FXML
    private Label topDateLabel;
    @FXML
    private Label srNoUpLabel;
    @FXML
    private Label upSsrAvgLabel;
    @FXML
    private Label nonBannerRateLabel;
    @FXML
    private ToggleSwitch ssrModelSwitch;

    @FXML
    private AnchorPane contentPane;

    @FXML
    private StackPane emptyPane,loadingPane;

    @FXML
    private StackPane poolEmptyPane;
    @FXML
    private HBox poolAnalysisPane;
    private boolean poolChange=false;//代码控制卡池切换标志
    private String pieLabel;
    private String barLabel;
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        emptyPane.visibleProperty().bind(viewModel.emptyProperty());
        loadingPane.visibleProperty().bind(viewModel.loadingProperty());
        BooleanBinding contentVisibleBinding = Bindings.and(viewModel.emptyProperty().not(), viewModel.loadingProperty().not());
        contentPane.visibleProperty().bind(contentVisibleBinding);
        poolEmptyPane.visibleProperty().bind(viewModel.poolEmptyProperty());
        poolAnalysisPane.visibleProperty().bind(viewModel.poolEmptyProperty().not());
        ssrModelSwitch.selectedProperty().bindBidirectional(viewModel.ssrModelProperty());
        totalCountLabel.textProperty().bind(viewModel.totalTextProperty());
        totalCostLabel.textProperty().bind(viewModel.totalCostTextProperty());
        currentCountLabel.textProperty().bind(viewModel.currentTextProperty());
        ssrLabel1.textProperty().bind(viewModel.ssrText1Property());
        ssrLabel2.textProperty().bind(viewModel.ssrText2Property());
        srLabel1.textProperty().bind(viewModel.srText1Property());
        srLabel2.textProperty().bind(viewModel.srText2Property());
        rLabel1.textProperty().bind(viewModel.rText1Property());
        rLabel2.textProperty().bind(viewModel.rText2Property());

        ssrAvgLabel.textProperty().bind(viewModel.ssrAvgTextProperty());
        ssrMaxLabel.textProperty().bind(viewModel.ssrMaxTextProperty());
        ssrMinLabel.textProperty().bind(viewModel.ssrMinTextProperty());
        upSsrAvgLabel.textProperty().bind(viewModel.upSsrAvgTextProperty());
        nonBannerRateLabel.textProperty().bind(viewModel.nonBannerRateTextProperty());
        topDateLabel.textProperty().bind(viewModel.poolDateTextProperty());
        srNoUpLabel.textProperty().bind(viewModel.srNoUpTextProperty());
        upCountLabel1.textProperty().bind(viewModel.upCountText1Property());
        upCountLabel2.textProperty().bind(viewModel.upCountText2Property());

        poolListview.setItems(viewModel.getPoolNameList());
        poolListview.setCellFactory(stringListView -> new PoolNameCell());
        poolListview.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
            if (t1 != null && t1.intValue() >= 0 && !poolChange){
                viewModel.changePool(t1.intValue());
            }
        });
        if (!poolListview.getItems().isEmpty()){
            poolListview.getSelectionModel().select(0);
        }
        setupChart(resourceBundle);

        ssrListView.setCellFactory(ssrDataListView -> new SsrCell());

        viewModel.subscribe("update", (s, objects) -> {
            poolChange=true;
            poolListview.getSelectionModel().select(0);
            poolChange=false;
        });

        reBind();
        ssrModelSwitch.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            reBind();
        });

        ssrFlowPane.visibleProperty().bind(ssrModelSwitch.selectedProperty().not());
        ssrListView.visibleProperty().bind(ssrModelSwitch.selectedProperty());

    }

    /**
     * 初始化图表自定义控件（图表类型 / 配色主题 / 数值标签），并绑定数据刷新。
     */
    private void setupChart(java.util.ResourceBundle rb){
        pieLabel = rb.getString("ui.analysis.chart.type.pie");
        barLabel = rb.getString("ui.analysis.chart.type.bar");
        chartTypeCombo.getItems().setAll(pieLabel, barLabel);
        chartTypeCombo.setValue(pieLabel);

        String[] themeKeys = {"default", "fresh", "dark", "contrast"};
        String[] themeLabels = {
                rb.getString("ui.analysis.chart.theme.default"),
                rb.getString("ui.analysis.chart.theme.fresh"),
                rb.getString("ui.analysis.chart.theme.dark"),
                rb.getString("ui.analysis.chart.theme.contrast")
        };
        themeCombo.getItems().setAll(themeLabels);
        themeCombo.setValue(themeLabels[0]);

        chartTypeCombo.valueProperty().addListener((o, v, n) -> buildChart(themeKeys));
        themeCombo.valueProperty().addListener((o, v, n) -> buildChart(themeKeys));
        showLabelsSwitch.selectedProperty().addListener((o, v, n) -> buildChart(themeKeys));
        viewModel.getPieChartData().addListener((javafx.collections.ListChangeListener<? super PieChart.Data>) c -> buildChart(themeKeys));
        viewModel.chartTitleProperty().addListener((o, v, n) -> buildChart(themeKeys));
        buildChart(themeKeys);
    }

    /** 配色主题：SSR / SR / R 三色 */
    private static final java.util.Map<String, String[]> THEMES = java.util.Map.of(
            "default",  new String[]{"#FFD24C", "#9AA7B8", "#C98A5E"},
            "fresh",    new String[]{"#3FB950", "#58A6FF", "#F0A23B"},
            "dark",     new String[]{"#FFE066", "#B392F0", "#768390"},
            "contrast", new String[]{"#FF5C8A", "#3B82F6", "#22D3EE"}
    );

    /**
     * 根据当前选择的图表类型 / 配色 / 标签开关，重建图表（饼图或柱状图）。
     */
    private void buildChart(String[] themeKeys){
        if (chartHolder == null) return;
        chartHolder.getChildren().clear();

        String themeLabel = themeCombo.getValue();
        int tIdx = Math.max(0, themeCombo.getItems().indexOf(themeLabel));
        String[] colors = THEMES.getOrDefault(themeKeys[tIdx], THEMES.get("default"));

        javafx.collections.ObservableList<PieChart.Data> data = viewModel.getPieChartData();
        boolean isBar = barLabel.equals(chartTypeCombo.getValue());
        boolean showLabels = showLabelsSwitch.isSelected();

        if (isBar){
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("");
            yAxis.setLabel("");
            BarChart<String, Number> bar = new BarChart<>(xAxis, yAxis);
            bar.setLegendVisible(true);
            bar.setAnimated(false);
            bar.setTitle(viewModel.getChartTitle());

            int idx = 0;
            for (PieChart.Data d : data){
                final int fi = idx;
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName(d.getName());
                s.getData().add(new XYChart.Data<>(d.getName(), d.getPieValue()));
                bar.getData().add(s);
                s.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null){
                        node.setStyle("-fx-bar-fill: " + colors[fi % 3] + ";");
                        if (showLabels) Tooltip.install(node, new Tooltip(d.getName() + ": " + d.getPieValue()));
                    }
                });
                if (s.getNode() != null){
                    s.getNode().setStyle("-fx-bar-fill: " + colors[fi % 3] + ";");
                    if (showLabels) Tooltip.install(s.getNode(), new Tooltip(d.getName() + ": " + d.getPieValue()));
                }
                idx++;
            }
            chartHolder.getChildren().add(bar);
        } else {
            PieChart pie = new PieChart(data);
            pie.setTitle(viewModel.getChartTitle());
            pie.setAnimated(false);
            pie.setClockwise(false);
            pie.setLabelsVisible(showLabels);
            int idx = 0;
            for (PieChart.Data d : pie.getData()){
                final int fi = idx;
                d.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null) node.setStyle("-fx-pie-color: " + colors[fi % 3] + ";");
                });
                if (d.getNode() != null) d.getNode().setStyle("-fx-pie-color: " + colors[fi % 3] + ";");
                idx++;
            }
            chartHolder.getChildren().add(pie);
        }
    }

    private void reBind(){
        if (ssrModelSwitch.isSelected()){
            ssrListView.setItems(viewModel.getSsrList());
            ssrFlowPane.getChildren().clear();
        }else {
            ssrListView.setItems(null);
            ssrFlowPane.getChildren().clear();
            for (SsrData ssrData :  viewModel.getSsrList()) {
                ssrFlowPane.getChildren().add(new SsrChildView(ssrData));
            }
            viewModel.getSsrList().addListener((ListChangeListener<? super SsrData>) change -> {
                if (!ssrModelSwitch.isSelected()){
                    ssrFlowPane.getChildren().clear();
                    for (SsrData ssrData : change.getList()) {
                        ssrFlowPane.getChildren().add(new SsrChildView(ssrData));
                    }
                }
            });
        }
    }




    class SsrChildView extends StackPane {
        private ImageView iv = new ImageView();
        private Label count = new Label();
        private Label name = new Label();
        private Label upBadge = new Label("UP");

        public SsrChildView(SsrData ssrData) {
            VBox vBox = new VBox(iv, name, count);
            vBox.setAlignment(Pos.TOP_CENTER);
            vBox.setSpacing(2);
            vBox.setFillWidth(true);
            name.setMaxWidth(Double.MAX_VALUE);

            upBadge.setVisible(ssrData.isEvent());
            upBadge.getStyleClass().add("thumb-up-badge");
            StackPane.setAlignment(upBadge, Pos.TOP_RIGHT);

            getChildren().addAll(vBox, upBadge);
            getStyleClass().add("child");
            iv.setFitWidth(70.0);
            iv.setFitHeight(70.0);
            iv.setImage(LocalResourcesManager.header(ssrData.getId(), 70, 70));
            name.setText(ssrData.getName());
            name.getStyleClass().add("thumb-name");
            count.setText(String.format("%02d", ssrData.getCount()));
            count.getStyleClass().add("count");
            count.getStyleClass().add(ssrData.isEvent() ? "thumb-count-up" : "thumb-count-unup");
        }
    }

    class SsrCell extends ListCell<SsrData> {
        private final HBox root;
        private ImageView iv;
        private Label name;
        private Label date;
        private Label count;
        private Label upLabel;
        private ProgressBar progressBar;

        public SsrCell() {
            iv = new ImageView();
            iv.setFitHeight(60);
            iv.setFitWidth(60);
            iv.setSmooth(true);
            Circle circle = new Circle(30, 30, 30);
            iv.setClip(circle);

            name = new Label();
            name.getStyleClass().add("role-name");
            date = new Label();
            date.getStyleClass().add("role-date");
            Spacer spacer = new Spacer();
            HBox top = new HBox(5.0, name, spacer, date);
            top.setAlignment(Pos.CENTER_LEFT);

            upLabel = new Label("UP!");
            upLabel.getStyleClass().add("up-tag");
            upLabel.setVisible(false);

            progressBar = new ProgressBar();
            progressBar.setProgress(0);
            progressBar.setPrefWidth(200);
            HBox progressHBox = new HBox(5.0, progressBar);
            progressHBox.setAlignment(Pos.CENTER);
            VBox parent = new VBox(5.0, top, progressHBox);
            parent.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(parent,Priority.ALWAYS);


            count = new Label();
            count.getStyleClass().add("role-desc");
            root = new HBox(15.0, iv, parent, count,upLabel);
            root.setAlignment(Pos.CENTER_LEFT);
            root.getStyleClass().add("role-pane");
        }

        @Override
        protected void updateItem(SsrData ssrData, boolean b) {
            super.updateItem(ssrData, b);
            if (!b) {
                Thread.startVirtualThread(() -> {
                    Image image = LocalResourcesManager.header(ssrData.getId(), 60, 60);
                    Platform.runLater(() -> iv.setImage(image));
                });

                name.setText(ssrData.getName());
                date.setText(ssrData.getDate());
                count.setText(String.format("%02d", ssrData.getCount()));
                progressBar.setProgress(ssrData.getCount() / 80.0);

                upLabel.setVisible(ssrData.isEvent());
                count.getStyleClass().removeAll("up", "unup");
                progressBar.getStyleClass().removeAll("up", "unup");
                if (ssrData.isEvent()) {
                    count.getStyleClass().add("up");
                    progressBar.getStyleClass().add("up");
                } else {
                    count.getStyleClass().add("unup");
                    progressBar.getStyleClass().add("unup");
                }
                setGraphic(root);
            } else {
                setGraphic(null);
            }
        }
    }

}