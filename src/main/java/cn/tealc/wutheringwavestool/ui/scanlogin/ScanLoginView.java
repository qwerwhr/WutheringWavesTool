package cn.tealc.wutheringwavestool.ui.scanlogin;

import cn.tealc.wutheringwavestool.WwtApp;
import cn.tealc.wutheringwavestool.base.AppInjector;
import cn.tealc.wutheringwavestool.base.NotificationKey;
import cn.tealc.wutheringwavestool.service.UserInfoService;
import com.kuro.scan.AccountManager;
import com.kuro.scan.KuRoService;
import com.kuro.scan.ScanAccount;
import com.kuro.scan.ScanCallback;
import com.kuro.kujiequ.model.sign.UserInfo;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.ViewTuple;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * 扫码登录页控制器，移植自 KuRo_Scanner 的 WindowMain。
 */
public class ScanLoginView implements FxmlView<ScanLoginViewModel>, Initializable, ScanCallback {

    @InjectViewModel
    private ScanLoginViewModel viewModel;

    @FXML
    private TableView<ScanAccount> accountTable;
    @FXML
    private TableColumn<ScanAccount, Number> colIndex;
    @FXML
    private TableColumn<ScanAccount, String> colUid;
    @FXML
    private TableColumn<ScanAccount, String> colName;
    @FXML
    private TableColumn<ScanAccount, String> colNote;
    @FXML
    private TableColumn<ScanAccount, String> colServer;
    @FXML
    private TableColumn<ScanAccount, String> colLevel;
    @FXML
    private Label selectedNameLabel;
    @FXML
    private ComboBox<String> platformCombo;
    @FXML
    private TextField liveIdField;
    @FXML
    private Button monitorScreenBtn;
    @FXML
    private Button monitorStreamBtn;
    @FXML
    private CheckBox autoLoginCheck;
    @FXML
    private CheckBox autoScreenCheck;
    @FXML
    private CheckBox autoExitCheck;
    @FXML
    private Label statusLabel;
    @FXML
    private Button addAccountBtn;
    @FXML
    private Button deleteAccountBtn;
    @FXML
    private Button setDefaultBtn;

    private String pendingQr;
    private boolean pendingFromScreen;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        viewModel.init();

        accountTable.setItems(viewModel.getAccountList());
        accountTable.setEditable(true);

        colUid.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getUid()));
        colName.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getName()));
        colNote.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getNote()));
        colServer.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getServer()));
        colLevel.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue().getLevel()));
        colNote.setCellFactory(col -> {
            TextFieldTableCell<ScanAccount, String> cell = new TextFieldTableCell<>();
            return cell;
        });
        colNote.setOnEditCommit(e -> {
            ScanAccount item = e.getRowValue();
            if (item == null) return;
            item.setNote(e.getNewValue());
            int idx = accountTable.getItems().indexOf(item);
            if (idx >= 0) {
                AccountManager.getInstance().setNote(idx, e.getNewValue());
            }
        });
        colIndex.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(
                accountTable.getItems().indexOf(cd.getValue()) + 1));

        accountTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        accountTable.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            int idx = nv.intValue();
            if (idx >= 0 && idx < accountTable.getItems().size()) {
                viewModel.selectAccount(idx, accountTable.getItems().get(idx).getName());
            } else {
                viewModel.selectAccount(-1, null);
            }
        });

        platformCombo.getItems().addAll("抖音", "BiliBili");
        platformCombo.getSelectionModel().select(1); // 默认 B站（抖音需要 a_bogus 暂不支持）
        liveIdField.setTextFormatter(new TextFormatter<>(change ->
                change.getText().matches("\\d*") ? change : null));

        selectedNameLabel.textProperty().bind(viewModel.selectedNameProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        autoLoginCheck.selectedProperty().bindBidirectional(viewModel.autoLoginProperty());
        autoScreenCheck.selectedProperty().bindBidirectional(viewModel.autoScreenProperty());
        autoExitCheck.selectedProperty().bindBidirectional(viewModel.autoExitProperty());

        monitorScreenBtn.textProperty().bind(
                javafx.beans.binding.Bindings.when(viewModel.screenRunningProperty())
                        .then("监视屏幕中（点击停止）").otherwise("监视屏幕"));
        monitorStreamBtn.textProperty().bind(
                javafx.beans.binding.Bindings.when(viewModel.streamRunningProperty())
                        .then("监视直播间中（点击停止）").otherwise("监视直播间"));

        addAccountBtn.setOnAction(e -> showAddAccountDialog());
        deleteAccountBtn.setOnAction(e -> viewModel.deleteSelected());
        setDefaultBtn.setOnAction(e -> viewModel.setDefault());
        monitorScreenBtn.setOnAction(e -> toggleScreen());
        monitorStreamBtn.setOnAction(e -> toggleStream());

        // 启动时自动监视屏幕
        if (viewModel.shouldAutoStartScreen()) {
            viewModel.selectAccount(accountTable.getSelectionModel().getSelectedIndex(), null);
            Platform.runLater(() -> startScreen());
        }

        // 监听账号管理页的账号变更通知，同步到扫码登录列表
        de.saxsys.mvvmfx.MvvmFX.getNotificationCenter().subscribe(NotificationKey.ACCOUNT_UPDATE, (s, objects) -> {
            Platform.runLater(() -> {
                UserInfoService userInfoService = AppInjector.getInstance(UserInfoService.class);
                viewModel.syncFromUserInfoService(userInfoService);
                statusLabel.setText("已从账号管理同步数据");
            });
        });
    }

    private void toggleScreen() {
        if (viewModel.screenRunningProperty().get()) {
            viewModel.stopScreen();
        } else {
            startScreen();
        }
    }

    private void startScreen() {
        if (!viewModel.startScreen(this)) {
            viewModel.onScanFinished();
        }
    }

    private void toggleStream() {
        if (viewModel.streamRunningProperty().get()) {
            viewModel.stopStream();
        } else {
            int platform = platformCombo.getSelectionModel().getSelectedIndex();
            if (!viewModel.startStream(platform, liveIdField.getText(), this)) {
                viewModel.onScanFinished();
            }
        }
    }

    // ---- ScanCallback ----
    @Override
    public void onQrRegistered(String qrCode, boolean fromScreen) {
        Platform.runLater(() -> {
            pendingQr = qrCode;
            pendingFromScreen = fromScreen;
            if (viewModel.autoLoginProperty().get()) {
                doConfirm(qrCode, true, "", fromScreen);
            } else {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("登录确认");
                alert.setHeaderText(null);
                alert.setContentText("正在使用账号「" + viewModel.selectedNameProperty().get()
                        + "」\n登录鸣潮\n确认登录？");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    doConfirm(qrCode, false, "", fromScreen);
                } else {
                    viewModel.stopScreen();
                    viewModel.stopStream();
                    viewModel.onScanFinished();
                }
            }
        });
    }

    @Override
    public void onLoginResult(KuRoService.ScanRet ret, boolean fromScreen) {
        Platform.runLater(() -> {
            switch (ret) {
                case SUCCESS -> showInfo("扫码成功！");
                case FAILURE_1 -> showInfo("扫码失败（第一步登记失败）");
                case FAILURE_2 -> showInfo("扫码二次确认失败");
                case LIVESTOP -> showInfo("直播中断");
                case STREAMERROR -> showInfo("直播流初始化失败");
                case NeedSMSCode -> showInfo("需要短信二次验证");
                default -> { }
            }
            viewModel.stopScreen();
            viewModel.stopStream();
            viewModel.onScanFinished();
            if (ret == KuRoService.ScanRet.SUCCESS && viewModel.autoExitProperty().get()) {
                WwtApp.exit();
            }
        });
    }

    @Override
    public void onStatus(String message) {
        Platform.runLater(() -> viewModel.statusMessageProperty().set(message));
    }

    private void doConfirm(String qrCode, boolean autoLogin, String sms, boolean fromScreen) {
        KuRoService.ScanRet ret = viewModel.confirmLogin(qrCode, autoLogin, sms, fromScreen);
        switch (ret) {
            case SUCCESS -> onLoginResult(KuRoService.ScanRet.SUCCESS, fromScreen);
            case NeedSMSCode -> showSmsDialog(qrCode, autoLogin, fromScreen);
            case FAILURE_2 -> onLoginResult(KuRoService.ScanRet.FAILURE_2, fromScreen);
            default -> onLoginResult(KuRoService.ScanRet.FAILURE_2, fromScreen);
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    private void showSmsDialog(String qrCode, boolean autoLogin, boolean fromScreen) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("短信验证");

        Label title = new Label("登录需要短信二次验证，请使用当前账号绑定手机号接收验证码");
        TextField codeField = new TextField();
        codeField.setPromptText("输入短信验证码");
        Button sendBtn = new Button("发送验证码");
        CheckBox remember = new CheckBox("记住本次验证");
        Button okBtn = new Button("确定");
        Button cancelBtn = new Button("取消");

        sendBtn.setOnAction(e -> {
            if (viewModel.sendScanSms()) {
                sendBtn.setDisable(true);
                sendBtn.setText("已发送");
            } else {
                sendBtn.setText("发送失败，重试");
            }
        });
        okBtn.setOnAction(e -> {
            doConfirm(qrCode, remember.isSelected(), codeField.getText().trim(), fromScreen);
            stage.close();
        });
        cancelBtn.setOnAction(e -> {
            viewModel.stopScreen();
            viewModel.stopStream();
            viewModel.onScanFinished();
            stage.close();
        });

        HBox inputRow = new HBox(10, codeField, sendBtn);
        HBox bottomRow = new HBox(10, okBtn, cancelBtn);
        bottomRow.setAlignment(javafx.geometry.Pos.CENTER);
        VBox box = new VBox(12, title, inputRow, remember, bottomRow);
        box.setPadding(new javafx.geometry.Insets(16));
        stage.setScene(new javafx.scene.Scene(box));
        stage.show();
    }

    private void showAddAccountDialog() {
        // 复用账号管理页的「添加库街区账号」对话框（验证码登录 / Token 登录）
        ViewTuple<cn.tealc.wutheringwavestool.ui.kujiequ.account.AccountUpdateView,
                cn.tealc.wutheringwavestool.ui.kujiequ.account.AccountUpdateViewModel> viewTuple =
                FluentViewLoader.fxmlView(
                        cn.tealc.wutheringwavestool.ui.kujiequ.account.AccountUpdateView.class)
                        .viewModel(new cn.tealc.wutheringwavestool.ui.kujiequ.account.AccountUpdateViewModel())
                        .load();
        de.saxsys.mvvmfx.MvvmFX.getNotificationCenter().publish(
                NotificationKey.DIALOG, viewTuple.getView(), viewTuple.getCodeBehind());
    }
}
