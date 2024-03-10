package com.okg.textrecognition;

import android.graphics.Rect;
import android.text.TextUtils;

import com.google.mlkit.vision.text.Text;

import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author okg
 * @date 2024-03-07
 * 描述：文本识别辅助类
 */
public class OCRHelper {
    private static final String TAG = "Mlkit-OCRHelper";
    private static final String STR_SPLIT_COLON = ":";
    private static final String STR_SPLIT_BLANK = " ";
    private static final String KEY_IMEI = "imei";
    public static final String KEY_IMEI1 = "imei1";
    public static final String KEY_IMEI2 = "imei2";
    public static final String KEY_SN = "sn";
    private static final String KEY_SN1 = "s/n";
    private static final String KEY_SN2 = "serial";
    private static final String KEY_SN_CHINESE = "序列号";

    private static final int TYPE_LAYOUT_CMD = 1;
    private static final int TYPE_LAYOUT_VERTICAL = 2;
    private static final int TYPE_LAYOUT_HORIZONTAL = 3;

    private static OCRHelper mInstance;

    private OCRHelper() {

    }

    public static OCRHelper getInstance() {
        if (mInstance == null) {
            synchronized (OCRHelper.class) {
                if (mInstance == null) {
                    mInstance = new OCRHelper();
                }
            }
        }
        return mInstance;
    }

    /**
     * 解析imei及sn序列号信息
     *
     * @param result
     * @return 返回识别的文本信息
     */
    public JSONObject parseImeiAndSnInfo(Text result) {
        CommonUtil.log(TAG, "parseText: " + result != null ? result.getText() : null);
        int blockCount = result == null ? 0 : result.getTextBlocks().size();
        if (blockCount == 0) {
            return new JSONObject();
        }
        int layoutType = detectTextLayoutByImei(result);
        JSONObject resultJson;
        switch (layoutType) {
            case TYPE_LAYOUT_CMD:
                resultJson = parseCMDDeviceInfo(result);
                break;
            case TYPE_LAYOUT_VERTICAL:
                resultJson = parseVerticalDeviceInfo(result);
                break;
            case TYPE_LAYOUT_HORIZONTAL:
                resultJson = parseHorizontalDeviceInfo(result);
                break;
            default:
                // 没有检测到imei信息，进一步探测是否存在sn序列号信息
                resultJson = parseSnInfo(result);
                break;
        }
        try {
            resultJson.put("layoutType", layoutType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultJson;
    }


    /**
     * 通过imei关键字来探测排行方向
     *
     * @return
     */
    private int detectTextLayoutByImei(Text result) {
        int blockCount = result.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = result.getTextBlocks().get(i);
            Rect rect = textBlock.getBoundingBox();
            CommonUtil.log(TAG, "blockNum: " + i + " ,top=" + rect.top + " ,bottom=" + rect.bottom + " ,centerY=" + rect.centerY() + "\n");
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            for (int j = 0; j < lineCount; j++) {
                Text.Line textLine = textBlock.getLines().get(j);
                String lineText = textLine.getText();
                // 文本转换为小写
                lineText = lineText.toLowerCase();
                CommonUtil.log(TAG, "lineNum: " + j + ", lineText = " + textLine.getText() + "\n");
                if (lineText.startsWith(KEY_IMEI)) {
                    CommonUtil.log(TAG, "=======检测到有imei关键字=======");
                    // step1 该行文本包含imei关键字，进一步探测，目前发现有以:或空格风格的情况
                    if (detectIsCmdLayout(lineText)) {
                        // 包含“:”，证明是通过*#06#命令查看的方式
                        CommonUtil.log(TAG, "检测到有imei关键字且包含:或空格，判定为通过命令行输入方式");
                        return TYPE_LAYOUT_CMD;
                    } else {
                        // step2 检测是否为横向的
                        boolean isHorizontalLayout = detectIsHorizontalLayoutByImei(rect, result);
                        if (isHorizontalLayout) {
                            CommonUtil.log(TAG, "判断为横向排版");
                            return TYPE_LAYOUT_HORIZONTAL;
                        } else {
                            CommonUtil.log(TAG, "判断为垂直排版");
                            return TYPE_LAYOUT_VERTICAL;
                        }
                    }
                }
            }
        }
        // 没有检测到imei
        return -1;
    }

    /**
     * 通过imei探测布局是否为横向布局
     *
     * @param imeiBlockRect
     * @param resultText
     * @return
     */
    private boolean detectIsHorizontalLayoutByImei(Rect imeiBlockRect, Text resultText) {
        int blockCount = resultText.getTextBlocks().size();
        for (int i = 0; i < blockCount; i++) {
            // 判断block是否包含imei关键字
            String blockText = resultText.getTextBlocks().get(i).getText();
            if (detectIsImeiByPrefix(blockText)) {
                Rect blockRect = resultText.getTextBlocks().get(i).getBoundingBox();
                if (isRectOverlap(imeiBlockRect, blockRect)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * cmd命令格式解析文本
     *
     * @param resultText
     * @return
     */
    private JSONObject parseCMDDeviceInfo(Text resultText) {
        CommonUtil.log(TAG, "=====parseCMDDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            for (int j = 0; j < lineCount; j++) {
                Text.Line textLine = textBlock.getLines().get(j);
                String lineText = textLine.getText();
                // 文本转换为小写
                lineText = lineText.toLowerCase();
                CommonUtil.log(TAG, "lineNum: " + j + ", lineText = " + textLine.getText() + "\n");
                if (lineText.startsWith(KEY_IMEI)) {
                    CommonUtil.log(TAG, "检测到有imei关键字, 需要进一步判断是否为imei1和imei2");
                    String[] textArrays = getCmdSplitArrays(lineText);
                    if (textArrays != null && textArrays.length > 1) {
                        if (lineText.contains(KEY_IMEI2)) {
                            imei2 = textArrays[1];
                        } else {
                            imei1 = textArrays[1];
                        }
                    }
                } else if (lineText.startsWith(KEY_SN) || lineText.startsWith(KEY_SN1)) {
                    CommonUtil.log(TAG, "检测到有sn关键字");
                    String[] textArrays = getCmdSplitArrays(lineText);
                    if (textArrays != null && textArrays.length > 1) {
                        sn = textArrays[1];
                    }
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        if (sn != null) {
            sn = sn.toUpperCase();
        }
        JSONObject resultJson = new JSONObject();
        try {
            resultJson.put(KEY_IMEI1, imei1);
            resultJson.put(KEY_IMEI2, imei2);
            resultJson.put(KEY_SN, sn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultJson;
    }

    /**
     * 垂直布局方向解析文本
     *
     * @param resultText
     * @return
     */
    private JSONObject parseVerticalDeviceInfo(Text resultText) {
        CommonUtil.log(TAG, "=====parseVerticalDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            CommonUtil.log(TAG, "blockText = " + textBlock.getText());
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            String firstLineText = textBlock.getLines().get(0).getText();
            CommonUtil.log(TAG, "LineCount=" + lineCount + " ,firstLineText:" + firstLineText);
            if (TextUtils.isEmpty(firstLineText)) {
                continue;
            }
            firstLineText = firstLineText.toLowerCase();
            if (firstLineText.startsWith(KEY_SN) || firstLineText.startsWith(KEY_SN1) || firstLineText.contains(KEY_SN_CHINESE)) {
                CommonUtil.log(TAG, "检测到有sn/序列号信息");
                // 下一行为序列号信息
                sn = getNextBlockLineText(resultText, i);
            } else if (firstLineText.contains(KEY_IMEI)) {
                CommonUtil.log(TAG, "检测到有imei信息");
                String nextLineOrBlockText = "";
                // 先判断本block中是否有imei信息
                if (lineCount > 1) {
                    for (int j = 1; j < lineCount; j++) {
                        String lineText = textBlock.getLines().get(j).getText();
                        if (detectIsImeiByPrefix(lineText)) {
                            nextLineOrBlockText = lineText;
                            break;
                        }
                    }

                }
                if (TextUtils.isEmpty(nextLineOrBlockText)) {
                    nextLineOrBlockText = getNextBlockLineText(resultText, i);
                }
                CommonUtil.log(TAG, "nextLineOrBlockText = " + nextLineOrBlockText);
                if (!TextUtils.isEmpty(nextLineOrBlockText)) {
                    if (TextUtils.isEmpty(imei1)) {
                        imei1 = nextLineOrBlockText;
                    } else {
                        imei2 = nextLineOrBlockText;
                    }
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        if (sn != null) {
            sn = sn.toUpperCase();
        }
        JSONObject resultJson = new JSONObject();
        try {
            resultJson.put(KEY_IMEI1, imei1);
            resultJson.put(KEY_IMEI2, imei2);
            resultJson.put(KEY_SN, sn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultJson;
    }

    /**
     * 横向布局方向解析文本
     *
     * @param resultText
     * @return
     */
    private JSONObject parseHorizontalDeviceInfo(Text resultText) {
        CommonUtil.log(TAG, "=====parseHorizontalDeviceInfo=====");
        String imei1 = "";
        String imei2 = "";
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找imei，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            String blockText = textBlock.getText();
            CommonUtil.log(TAG, "blockText = " + blockText);
            if (TextUtils.isEmpty(blockText)) {
                continue;
            }
            blockText = blockText.toLowerCase();
            if (blockText.startsWith(KEY_SN) || blockText.startsWith(KEY_SN1) || blockText.contains(KEY_SN_CHINESE)) {
                CommonUtil.log(TAG, "检测到有sn/序列号信息");
                // 下一行为序列号信息
                sn = getHorizontalBlockText(resultText, textBlock.getBoundingBox());
            } else if (blockText.contains(KEY_IMEI)) {
                String imei = getHorizontalBlockText(resultText, textBlock.getBoundingBox());
                if (TextUtils.isEmpty(imei1)) {
                    imei1 = imei;
                } else {
                    imei2 = imei;
                }
            }
            if (!TextUtils.isEmpty(imei1) && !TextUtils.isEmpty(imei2) && !TextUtils.isEmpty(sn)) {
                // 已全部获取，提前退出循环
                break;
            }
        }
        if (sn != null) {
            sn = sn.toUpperCase();
        }
        JSONObject resultJson = new JSONObject();
        try {
            resultJson.put(KEY_IMEI1, imei1);
            resultJson.put(KEY_IMEI2, imei2);
            resultJson.put(KEY_SN, sn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultJson;
    }

    /**
     * 解析SN信息
     *
     * @param resultText
     * @return
     */
    private JSONObject parseSnInfo(Text resultText) {
        CommonUtil.log(TAG, "=====parseSnInfo，没有imei信息，进一步检索检测是否有imei信息=====");
        String sn = "";
        int blockCount = resultText.getTextBlocks().size();
        // 先遍历查找sn，并确定布局方向
        for (int i = 0; i < blockCount; i++) {
            Text.TextBlock textBlock = resultText.getTextBlocks().get(i);
            Rect rect = textBlock.getBoundingBox();
            CommonUtil.log(TAG, "blockNum: " + i + " ,top=" + rect.top + " ,bottom=" + rect.bottom + " ,centerY=" + rect.centerY() + "\n");
            int lineCount = textBlock.getLines().size();
            if (lineCount == 0) {
                continue;
            }
            for (int j = 0; j < lineCount; j++) {
                Text.Line textLine = textBlock.getLines().get(j);
                String lineText = textLine.getText();
                // 文本转换为小写
                lineText = lineText.toLowerCase();
                CommonUtil.log(TAG, "lineNum: " + j + ", lineText = " + textLine.getText() + "\n");
                String snStr = getSnSubStr(lineText);
                CommonUtil.log(TAG, "检测SN子串：" + snStr);
                if (TextUtils.isEmpty(snStr)) {
                    continue;
                }
                // step1: 判断是否为命令行形式
                String[] textArray = getCmdSplitArrays(snStr);
                if (textArray != null && textArray.length > 1 && !TextUtils.isEmpty(textArray[1])) {
                    sn = textArray[1];
                    CommonUtil.log(TAG, "通过命令行方式检索到sn信息：" + sn);
                    break;
                } else {
                    // 进一步判断是否存在存在横向和垂直方向的sn信息
                    // 先横向查找
                    String sn1 = getHorizontalBlockText(resultText, textBlock.getBoundingBox());
                    CommonUtil.log(TAG, "尝试横向查找到的sn信息：" + sn1);
                    if (detectIsSn(sn1)) {
                        sn = sn1;
                        CommonUtil.log(TAG, "横向已查找到的sn信息，结束寻找===");
                        break;
                    }
                    CommonUtil.log(TAG, "尝试垂直查找sn");
                    // 先判断本block中是否有sn信息
                    if (j < (lineCount - 1)) {
                        // 当前文本不是最后一行，直接获取下一行文本信息
                        for (int k = j + 1; k < lineCount; k++) {
                            String text = textBlock.getLines().get(k).getText();
                            if (detectIsSn(text)) {
                                CommonUtil.log(TAG, "在本文本block垂直查找到sn，结束寻找");
                                sn = text;
                                break;
                            }
                        }

                    }
                    sn1 = getNextBlockLineText(resultText, i);
                    if (detectIsSn(sn1)) {
                        sn = sn1;
                        CommonUtil.log(TAG, "在下一文本block垂直查找到sn，结束寻找");
                        break;
                    }
                }
            }
            if (!TextUtils.isEmpty(sn)) {
                break;
            }
        }
        if (sn != null) {
            sn = sn.toUpperCase();
        }
        JSONObject resultJson = new JSONObject();
        try {
            resultJson.put(KEY_IMEI1, "");
            resultJson.put(KEY_IMEI2, "");
            resultJson.put(KEY_SN, sn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultJson;
    }

    /**
     * 获取sn子串
     *
     * @param lineText
     * @return
     */
    private String getSnSubStr(String lineText) {
        String snSubStr = "";
        if (TextUtils.isEmpty(lineText)) {
            return snSubStr;
        }
        // 截取sn子串
        if (lineText.contains(KEY_SN_CHINESE)) {
            snSubStr = lineText.substring(lineText.indexOf(KEY_SN_CHINESE));
        } else if (lineText.contains(KEY_SN2)) {
            snSubStr = lineText.substring(lineText.indexOf(KEY_SN2));
        } else if (lineText.contains(KEY_SN1)) {
            snSubStr = lineText.substring(lineText.indexOf(KEY_SN1));
        } else if (lineText.contains(KEY_SN)) {
            snSubStr = lineText.substring(lineText.indexOf(KEY_SN));
        }
        return snSubStr;
    }

    /**
     * 获取下一行文本块的第一行信息
     *
     * @param resultText
     * @param currentBlockIndex
     * @return
     */
    private String getNextBlockLineText(Text resultText, int currentBlockIndex) {
        String result = "";
        if (currentBlockIndex < resultText.getTextBlocks().size() - 1) {
            Text.TextBlock nextBlock = resultText.getTextBlocks().get((currentBlockIndex + 1));
            List<Text.Line> nextBlockLines = nextBlock.getLines();
            if (nextBlockLines != null && nextBlockLines.size() > 0) {
                result = nextBlockLines.get(0).getText();
            }
        }
        return result;
    }

    /**
     * 获取横向对应block的文本块信息
     *
     * @param resultText
     * @param currentBlockRect
     * @return
     */
    private String getHorizontalBlockText(Text resultText, Rect currentBlockRect) {
        int blockCount = resultText.getTextBlocks().size();
        for (int i = 0; i < blockCount; i++) {
            Rect blockRect = resultText.getTextBlocks().get(i).getBoundingBox();
            if (currentBlockRect != blockRect && isRectOverlap(currentBlockRect, blockRect)) {
                return resultText.getTextBlocks().get(i).getText();
            }
        }
        CommonUtil.log(TAG, "判断为横向，但找不到横向对应的block，请检查程序逻辑");
        return "";
    }

    /**
     * 判断两个矩形在y轴上是否存在交叉/重叠
     *
     * @param rect1
     * @param rect2
     * @return
     */
    private boolean isRectOverlap(Rect rect1, Rect rect2) {
        int top1 = rect1.top;
        int bottom1 = rect1.bottom;
        int centerY1 = rect1.centerY();
        int top2 = rect2.top;
        int bottom2 = rect2.bottom;
        int centerY2 = rect2.centerY();
        if ((centerY1 >= top2) && (centerY1 <= bottom2) && (centerY2 >= top1) && (centerY2 <= bottom1)) {
            return true;
        }
        if ((top1 > top2 && top1 < bottom2) || (bottom1 > top2 && bottom1 < bottom2) || (top2 > top1 && top2 < bottom1) || (bottom2 > top1 && bottom2 < bottom1)) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否为*#06#命令行查看形式
     * 目前发现cmd形式，大多通过:或空格符号分割字符串
     *
     * @param text
     * @return
     */
    private boolean detectIsCmdLayout(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        if (text.contains(STR_SPLIT_COLON)) {
            return true;
        }
        if (text.contains(STR_SPLIT_BLANK)) {
            String[] textArrays = text.split(STR_SPLIT_BLANK);
            if (textArrays != null && textArrays.length > 1 && detectIsImeiByPrefix(textArrays[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取cmd分串数组
     *
     * @param text
     * @return
     */
    private String[] getCmdSplitArrays(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String[] textArrays = text.split(STR_SPLIT_COLON);
        if (textArrays == null || textArrays.length < 2) {
            // 进一步查看空格换行符的情况
            textArrays = text.split(STR_SPLIT_BLANK);
        }
        return textArrays;
    }

    /**
     * 通过前缀判断一个字符串是否疑似为imei
     *
     * @param text
     * @return
     */
    private boolean detectIsImeiByPrefix(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        text = text.trim();
        return text.startsWith("86") || text.startsWith("35") || text.startsWith("01") || text.startsWith("99");
    }

    /**
     * 判断文本是否为疑似sn序列号信息
     *
     * @param text
     * @return
     */
    private boolean detectIsSn(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        if (text.length() < 10 || text.length() > 20) {
            CommonUtil.log(TAG, "字符串长度不在[10,20]之间，不认定为sn信息");
            return false;
        }
        // 验证是否包含大小写字母数字特殊字符，包括/、-、_等特殊字符
        Pattern pattern = Pattern.compile("^[A-Za-z0-9_/-]+$");
        Matcher matcher = pattern.matcher(text);
        return matcher.matches();
    }
}
