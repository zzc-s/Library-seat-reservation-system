/*
 Navicat Premium Data Transfer

 Source Server         : zc
 Source Server Type    : MySQL
 Source Server Version : 80013 (8.0.13)
 Source Host           : localhost:3306
 Source Schema         : library_seat

 Target Server Type    : MySQL
 Target Server Version : 80013 (8.0.13)
 File Encoding         : 65001

 Date: 27/04/2026 11:57:52
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for attendance_log
-- ----------------------------
DROP TABLE IF EXISTS `attendance_log`;
CREATE TABLE `attendance_log`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `reservation_id` bigint(20) NOT NULL,
  `seat_id` bigint(20) NOT NULL,
  `action` enum('CHECK_IN','CHECK_OUT','LEAVE_START','LEAVE_END','SIGN_IN','TEMP_LEAVE','RETURN','SIGN_OUT') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_al_res`(`reservation_id` ASC) USING BTREE,
  INDEX `fk_al_seat`(`seat_id` ASC) USING BTREE,
  INDEX `idx_al_user_time`(`user_id` ASC, `occurred_at` ASC) USING BTREE,
  CONSTRAINT `fk_al_res` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_al_seat` FOREIGN KEY (`seat_id`) REFERENCES `seat` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_al_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of attendance_log
-- ----------------------------

-- ----------------------------
-- Table structure for book
-- ----------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `isbn` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `author` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `is_borrowable` tinyint(1) NOT NULL DEFAULT 0,
  `hot_score` int(11) NULL DEFAULT 0,
  `stock` int(11) NOT NULL DEFAULT 0 COMMENT '库存数量',
  `publisher` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出版社',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图书分类',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图书封面图片URL',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '图书内容简介',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `isbn`(`isbn` ASC) USING BTREE,
  UNIQUE INDEX `idx_isbn`(`isbn` ASC) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of book
-- ----------------------------
INSERT INTO `book` VALUES (1, '9787020002207', '红楼梦', '曹雪芹', 1, 100, 5, '人民文学出版社', '小说', NULL, '《红楼梦》是中国古典四大名著之一，是一部具有高度思想性和艺术性的伟大作品。全书以贾、史、王、薛四大家族的兴衰为背景，以贾宝玉、林黛玉、薛宝钗的爱情婚姻故事为主线，描绘了一批举止见识出于须眉之上的闺阁佳人的人生百态，展现了真正的人性美和悲剧美，可以说是一部从各个角度展现女性美以及中国古代社会世态百相的史诗性著作。', '2026-01-13 13:36:16', '2026-04-24 16:03:53');
INSERT INTO `book` VALUES (2, '9787020008735', '西游记', '吴承恩', 1, 95, 5, '人民文学出版社', '小说', NULL, '《西游记》是中国古代第一部浪漫主义章回体长篇神魔小说，作者是明代吴承恩。全书主要描写了孙悟空出世及大闹天宫后，遇见了唐僧、猪八戒、沙僧和白龙马，西行取经，一路上历经艰险、降妖伏魔，经历了九九八十一难，终于到达西天见到如来佛祖，最终五圣成真的故事。', '2026-01-13 13:36:16', '2026-04-18 11:01:59');
INSERT INTO `book` VALUES (3, '9787020008742', '水浒传', '施耐庵', 1, 90, 5, '人民文学出版社', '小说', NULL, '《水浒传》是中国四大名著之一，作者是元末明初的施耐庵。全书描写了北宋末年以宋江为首的108位好汉在梁山聚义，以及聚义之后接受招安、四处征战的故事。小说通过描写梁山好汉反抗欺压、水泊梁山壮大和受宋朝招安，以及受招安后为宋朝征战，最终消亡的宏大故事，艺术地反映了中国历史上宋江起义从发生、发展直至失败的全过程。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (4, '9787020002191', '三国演义', '罗贯中', 1, 95, 5, '人民文学出版社', '历史大类', NULL, '《三国演义》是中国古典四大名著之一，是中国第一部长篇章回体历史演义小说，作者是元末明初的罗贯中。全书描写了从东汉末年到西晋初年之间近百年的历史风云，以描写战争为主，诉说了东汉末年的群雄割据混战和魏、蜀、吴三国之间的政治和军事斗争，最终司马炎一统三国，建立晋朝的故事。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (5, '9787020008738', '聊斋志异', '蒲松龄', 1, 85, 5, '人民文学出版社', '小说', NULL, '《聊斋志异》是中国清代小说家蒲松龄创作的文言短篇小说集。全书共有短篇小说491篇，它们或者揭露封建统治的黑暗，或者抨击科举制度的腐朽，或者反抗封建礼教的束缚，具有丰富深刻的思想内容。作品成功地塑造了众多的艺术典型，人物形象鲜明生动，故事情节曲折离奇，结构布局严谨巧妙，文笔简练，描写细腻，堪称中国古典文言短篇小说之巅峰。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (6, '9787020008745', '儒林外史', '吴敬梓', 1, 80, 5, '人民文学出版社', '小说', NULL, '《儒林外史》是清代吴敬梓创作的长篇小说，全书共五十六回，以写实主义描绘各类人士对于\"功名富贵\"的不同表现，一方面真实的揭示人性被腐蚀的过程和原因，从而对当时吏治的腐败、科举的弊端礼教的虚伪等进行了深刻的批判和嘲讽；一方面热情地歌颂了少数人物以坚持自我的方式所作的对于人性的守护，从而寄寓了作者的理想。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (7, '9787020008746', '骆驼祥子', '老舍', 1, 88, 5, '人民文学出版社', '文学', NULL, '《骆驼祥子》是老舍的代表作之一，以现实主义的笔法与悲天悯人的情怀，塑造了祥子、虎妞等一批令人难忘的艺术形象，在中国现代文学史上拥有重要地位。小说讲述的是中国北平城里的一个年轻好强、充满生命活力的人力车夫祥子三起三落的人生经历。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (8, '9787020008747', '围城', '钱钟书', 1, 90, 5, '人民文学出版社', '文学', NULL, '《围城》是钱钟书所著的长篇小说，是中国现代文学史上一部风格独特的讽刺小说。被誉为\"新儒林外史\"。小说以方鸿渐为中心，描绘了一群留学生和大学教授在生活和事业上的种种遭遇，揭示了抗战初期知识分子的群相。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (9, '9787020008748', '平凡的世界', '路遥', 1, 92, 5, '人民文学出版社', '文学', NULL, '《平凡的世界》是中国作家路遥创作的一部全景式地表现中国当代城乡社会生活的百万字长篇小说。全书共三部。该书以中国70年代中期到80年代中期十年间为背景，通过复杂的矛盾纠葛，以孙少安和孙少平两兄弟为中心，刻画了当时社会各阶层众多普通人的形象；劳动与爱情、挫折与追求、痛苦与欢乐、日常生活与巨大社会冲突纷繁地交织在一起，深刻地展示了普通人在大时代历史进程中所走过的艰难曲折的道路。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (10, '9787020008749', '活着', '余华', 1, 93, 5, '人民文学出版社', '文学', NULL, '《活着》是作家余华的代表作之一，讲述了在大时代背景下，随着内战、三反五反，大跃进，文化大革命等社会变革，徐福贵的人生和家庭不断经受着苦难，到了最后所有亲人都先后离他而去，仅剩下他和一头老牛相依为命。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (11, '9787532741934', '老人与海', '海明威', 1, 95, 5, '上海译文出版社', '外国小说', NULL, '《老人与海》是美国作家海明威于1951年在古巴写的一篇中篇小说，于1952年出版。该作围绕一位老年古巴渔夫，与一条巨大的马林鱼在离岸很远的湾流中搏斗而展开故事的讲述。它奠定了海明威在世界文学中的突出地位，这篇小说相继获得了1953年美国普利策奖和1954年诺贝尔文学奖。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (12, '9787532741935', '小王子', '安托万·德·圣埃克苏佩里', 1, 98, 5, '上海译文出版社', '外国小说', NULL, '《小王子》是法国作家安托万·德·圣埃克苏佩里于1942年写成的著名儿童文学短篇小说。本书的主人公是来自外星球的小王子。书中以一位飞行员作为故事叙述者，讲述了小王子从自己星球出发前往地球的过程中，所经历的各种历险。作者以小王子的孩子式的眼光，透视出成人的空虚、盲目，愚妄和死板教条，用浅显天真的语言写出了人类的孤独寂寞、没有根基随风流浪的命运。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (13, '9787532741936', '童年', '高尔基', 1, 88, 5, '上海译文出版社', '外国小说', NULL, '《童年》是苏联作家马克西姆·高尔基以自身经历为原型创作的自传体小说三部曲中的第一部。该作讲述了阿廖沙（高尔基的乳名）三岁到十岁这一时期的童年生活，生动地再现了19世纪七八十年代沙俄下层人民的生活状况，写出了高尔基对苦难的认识，对社会人生的独特见解，字里行间涌动着一股生生不息的热望与坚强。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (14, '9787532741937', '简·爱', '夏洛蒂·勃朗特', 1, 90, 5, '上海译文出版社', '外国小说', NULL, '《简·爱》是英国女作家夏洛蒂·勃朗特创作的长篇小说，是一部具有自传色彩的作品。作品讲述一位从小变成孤儿的英国女子在各种磨难中不断追求自由与尊严，坚持自我，最终获得幸福的故事。小说引人入胜地展示了男女主人公曲折起伏的爱情经历，歌颂了摆脱一切旧习俗和偏见，成功塑造了一个敢于反抗，敢于争取自由和平等地位的妇女形象。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (15, '9787532741938', '傲慢与偏见', '简·奥斯汀', 1, 92, 5, '上海译文出版社', '外国小说', NULL, '《傲慢与偏见》是英国女小说家简·奥斯汀的创作的长篇小说。小说描写了小乡绅班纳特五个待字闺中的千金，主角是二女儿伊丽莎白。她在舞会上认识了达西，但是耳闻他为人傲慢，一直对他心生排斥，经历一番周折，伊丽莎白解除了对达西的偏见，达西也放下傲慢，有情人终成眷属。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (16, '9787532741939', '呼啸山庄', '艾米莉·勃朗特', 1, 85, 5, '上海译文出版社', '外国小说', NULL, '《呼啸山庄》是英国女作家勃朗特姐妹之一艾米莉·勃朗特的作品，是19世纪英国文学的代表作之一。小说描写吉卜赛弃儿希斯克利夫被山庄老主人收养后，因受辱和恋爱不遂，外出致富，回来后对与其女友凯瑟琳结婚的地主林顿及其子女进行报复的故事。全篇充满强烈的反压迫、争幸福的斗争精神，又始终笼罩着离奇、紧张的浪漫气氛。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (17, '9787532741940', '悲惨世界', '维克多·雨果', 1, 93, 5, '上海译文出版社', '外国小说', NULL, '《悲惨世界》是由法国作家维克多·雨果在1862年发表的一部长篇小说，其内容涵盖了拿破仑战争和之后的十几年的时间。故事的主线围绕主人公土伦苦刑犯冉·阿让的个人经历，融进了法国的历史、革命、战争、道德哲学、法律、正义、宗教信仰。该作多次被改编演绎成影视作品。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (18, '9787532741941', '巴黎圣母院', '维克多·雨果', 1, 88, 5, '上海译文出版社', '外国小说', NULL, '《巴黎圣母院》是法国文学家维克多·雨果所著小说，在1831年1月14日出版的小说。故事的场景设定在1482年的巴黎圣母院，内容环绕一名吉卜赛少女爱丝梅拉达和由副主教克洛德·弗洛罗养大的圣母院驼背敲钟人卡西莫多。此故事曾多次被改编成电影、电视剧及音乐剧。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (19, '9787532741942', '百年孤独', '加西亚·马尔克斯', 1, 95, 5, '南海出版公司', '外国小说', NULL, '《百年孤独》是哥伦比亚作家加西亚·马尔克斯创作的长篇小说，是其代表作，也是拉丁美洲魔幻现实主义文学的代表作，被誉为\"再现拉丁美洲历史社会图景的鸿篇巨著\"。作品描写了布恩迪亚家族七代人的传奇故事，以及加勒比海沿岸小镇马孔多的百年兴衰，反映了拉丁美洲一个世纪以来风云变幻的历史。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (20, '9787532741943', '战争与和平', '列夫·托尔斯泰', 1, 90, 5, '上海译文出版社', '外国小说', NULL, '《战争与和平》是俄国作家列夫·托尔斯泰创作的长篇小说，也是其代表作。该作以1812年的卫国战争为中心，反映从1805到1820年间的重大历史事件。以鲍尔康斯、别祖霍夫、罗斯托夫和库拉金四大贵族的经历为主线，在战争与和平的交替描写中把众多的事件和人物串联起来。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (21, '9787020008750', '三体', '刘慈欣', 1, 0, 0, '重庆出版社', '科幻小说', '', '《三体》是刘慈欣创作的系列长篇科幻小说，由《三体》《三体2：黑暗森林》《三体3：死神永生》组成。作品讲述了地球人类文明和三体文明的信息交流、生死搏杀及两个文明在宇宙中的兴衰历程。其第一部经过刘宇昆翻译后获得了第73届雨果奖最佳长篇小说奖。', '2026-01-13 13:36:16', '2026-01-13 14:27:09');
INSERT INTO `book` VALUES (22, '9787020008751', '流浪地球', '刘慈欣', 1, 98, 1, '重庆出版社', '科幻小说', '', '《流浪地球》是刘慈欣创作的中篇小说，讲述了太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新家园。然而宇宙之路危险重重，为了拯救地球，为了人类能在漫长的2500年后抵达新的家园，流浪地球时代的年轻人挺身而出，展开争分夺秒的生死之战。', '2026-01-13 13:36:16', '2026-04-17 16:29:37');
INSERT INTO `book` VALUES (23, '9787020008752', '球状闪电', '刘慈欣', 1, 97, 1, '重庆出版社', '科幻小说', '', '《球状闪电》是刘慈欣创作的长篇科幻小说，小说描述了一个历经球状闪电的男主角对其历尽艰辛的研究历程，向我们展现了一个独特、神秘而离奇的世界。', '2026-01-13 13:36:16', '2026-04-17 16:30:26');
INSERT INTO `book` VALUES (24, '9787020008753', '白夜行', '东野圭吾', 0, 0, 0, '南海出版公司', '悬疑小说', NULL, '《白夜行》是日本作家东野圭吾创作的长篇小说，也是其代表作。该小说于1997年1月至1999年1月间连载于期刊，单行本1999年8月在日本发行。故事围绕着一对有着不同寻常情愫的小学生展开。1973年，大阪的一栋废弃建筑内发现了一具男尸，此后19年，嫌疑人之女雪穗与被害者之子桐原亮司走上截然不同的人生道路，一个跻身上流社会，一个却在底层游走，而他们身边的人，却接二连三地离奇死去，警察经过19年的艰苦追踪，终于使真相大白。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (25, '9787020008754', '解忧杂货店', '东野圭吾', 0, 0, 0, '南海出版公司', '小说', NULL, '《解忧杂货店》是日本作家东野圭吾写作的奇幻温情小说。该书讲述了在僻静街道旁的一家杂货店，只要写下烦恼投进店前门卷帘门的投信口，第二天就会在店后的牛奶箱里得到回答。现代人内心流失的东西，这家杂货店能帮你找回。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (26, '9787020008755', '嫌疑人X的献身', '东野圭吾', 0, 0, 0, '南海出版公司', '悬疑小说', NULL, '《嫌疑人X的献身》是日本推理小说作家东野圭吾创作的长篇推理小说，也是\"伽利略系列\"的第三本小说。该作讲述一个数学天才为了帮助一对母女隐藏杀害前夫的罪行，和警方展开了一连串的斗智，制造整个骗局。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (27, '9787020008756', '1984', '乔治·奥威尔', 0, 0, 0, '上海译文出版社', '外国小说', NULL, '《1984》是英国作家乔治·奥威尔创作的一部反乌托邦小说，出版于1949年。小说刻画了一个令人感到窒息和恐怖的，以追逐权力为最终目标的假想的极权主义社会。这部小说与英国作家赫胥黎著作的《美丽新世界》，以及俄国作家扎米亚京著作的《我们》并称反乌托邦的三部代表作。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (28, '9787020008757', '动物农场', '乔治·奥威尔', 0, 0, 0, '上海译文出版社', '外国小说', NULL, '《动物农场》是英国作家乔治·奥威尔创作的中篇小说，出版于1945年。该作讲述农场的一群动物成功地进行了一场\"革命\"，将压榨他们的人类东家赶出农场，建立起一个平等的动物社会。然而，动物领袖，那些聪明的猪们最终却篡夺了革命的果实，成为比人类东家更加独裁和极权的统治者。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (29, '9787020008758', '追风筝的人', '卡勒德·胡赛尼', 0, 0, 0, '上海人民出版社', '外国小说', NULL, '《追风筝的人》是美籍阿富汗作家卡勒德·胡赛尼的第一部长篇小说，译者李继宏，于2003年出版，是美国2005年的排名第三的最畅销书。全书围绕风筝与阿富汗两个少年之间展开，一个富家少年与家中仆人关于风筝的故事，关于人性的背叛与救赎。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `book` VALUES (30, '9787020008759', '挪威的森林', '村上春树', 0, 0, 0, '上海译文出版社', '外国小说', NULL, '《挪威的森林》是日本作家村上春树于1987年所著的一部长篇爱情小说。故事讲述主角渡边纠缠在情绪不稳定且患有精神疾病的直子和开朗活泼的小林绿子之间，展开了自我成长的旅程。', '2026-01-13 13:36:16', '2026-01-13 13:36:16');

-- ----------------------------
-- Table structure for borrow
-- ----------------------------
DROP TABLE IF EXISTS `borrow`;
CREATE TABLE `borrow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `book_id` bigint(20) NOT NULL,
  `borrow_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `due_date` datetime NOT NULL,
  `return_date` datetime NULL DEFAULT NULL,
  `status` enum('BORROWED','RETURNED','OVERDUE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BORROWED',
  `warning_count` int(11) NOT NULL DEFAULT 0 COMMENT '警告次数',
  `last_warning_at` datetime NULL DEFAULT NULL COMMENT '最后警告时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_book_id`(`book_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_borrow_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_borrow_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of borrow
-- ----------------------------

-- ----------------------------
-- Table structure for favorite
-- ----------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `book_id` bigint(20) NOT NULL COMMENT '图书ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_book`(`user_id` ASC, `book_id` ASC) USING BTREE COMMENT '用户和图书的唯一组合，防止重复收藏',
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_book_id`(`book_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  CONSTRAINT `fk_favorite_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of favorite
-- ----------------------------

-- ----------------------------
-- Table structure for feedback
-- ----------------------------
DROP TABLE IF EXISTS `feedback`;
CREATE TABLE `feedback`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `type` enum('FACILITY','SERVICE','BOOK','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT '反馈类型：设施/服务/图书/其他',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `admin_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '管理员回复',
  `user_reply` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '用户对管理员回复的回复',
  `is_private` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否隐私：TRUE=隐私（仅管理员可见），FALSE=公开',
  `response` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` enum('PENDING','PROCESSED','CLOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：待处理/已处理/已关闭',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of feedback
-- ----------------------------

-- ----------------------------
-- Table structure for group_join_request
-- ----------------------------
DROP TABLE IF EXISTS `group_join_request`;
CREATE TABLE `group_join_request`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED','EXPIRED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_gjr_group_user`(`group_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_gjr_group_status`(`group_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_gjr_user_status`(`user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_gjr_created_at`(`created_at` ASC) USING BTREE,
  CONSTRAINT `fk_gjr_group` FOREIGN KEY (`group_id`) REFERENCES `study_group` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_gjr_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of group_join_request
-- ----------------------------

-- ----------------------------
-- Table structure for group_member
-- ----------------------------
DROP TABLE IF EXISTS `group_member`;
CREATE TABLE `group_member`  (
  `group_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `role` enum('LEADER','MEMBER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：LEADER=组长，MEMBER=成员',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`group_id`, `user_id`) USING BTREE,
  INDEX `fk_member_user`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_member_group` FOREIGN KEY (`group_id`) REFERENCES `study_group` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of group_member
-- ----------------------------

-- ----------------------------
-- Table structure for group_notification
-- ----------------------------
DROP TABLE IF EXISTS `group_notification`;
CREATE TABLE `group_notification`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `group_id` bigint(20) NOT NULL,
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型: JOIN_REQUEST, JOIN_APPROVED, JOIN_REJECTED, GROUP_DELETED',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_gn_user_read`(`user_id` ASC, `is_read` ASC) USING BTREE,
  INDEX `idx_gn_group_user`(`group_id` ASC, `user_id` ASC) USING BTREE,
  CONSTRAINT `fk_gn_group` FOREIGN KEY (`group_id`) REFERENCES `study_group` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_gn_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of group_notification
-- ----------------------------

-- ----------------------------
-- Table structure for group_reservation
-- ----------------------------
DROP TABLE IF EXISTS `group_reservation`;
CREATE TABLE `group_reservation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `seat_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` enum('PENDING','CONFIRMED','CANCELLED','EXPIRED','COMPLETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_gr_group`(`group_id` ASC) USING BTREE,
  CONSTRAINT `fk_gr_group` FOREIGN KEY (`group_id`) REFERENCES `study_group` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of group_reservation
-- ----------------------------

-- ----------------------------
-- Table structure for notice
-- ----------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('NORMAL','URGENT','CLOSURE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL',
  `priority` int(11) NOT NULL DEFAULT 0,
  `is_published` tinyint(1) NOT NULL DEFAULT 0,
  `published_at` datetime NULL DEFAULT NULL,
  `expires_at` datetime NULL DEFAULT NULL COMMENT '到期时间，NULL 表示永不过期；到期后定时任务将 is_published 置为 0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_published`(`is_published` ASC, `published_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of notice
-- ----------------------------
INSERT INTO `notice` VALUES (1, '欢迎使用图书馆座位预约系统', '亲爱的读者朋友们，欢迎使用我们的图书馆座位预约系统！\n\n本系统提供以下功能：\n1. 在线选座预约\n2. 协同预约（小组学习）\n3. 图书借阅管理\n4. 反馈建议\n\n请遵守图书馆规章制度，保持安静的学习环境。祝您学习愉快！', 'NORMAL', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (2, '图书馆开放时间通知', '图书馆开放时间：\n\n周一至周五：8:00 - 22:00\n周六、周日：9:00 - 21:00\n\n节假日开放时间请关注后续通知。\n\n感谢您的理解与配合！', 'NORMAL', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (3, '座位预约使用须知', '座位预约使用须知：\n\n1. 请提前预约座位，预约成功后请按时到达\n2. 如需取消预约，请提前至少30分钟操作\n3. 预约时长最长4小时\n4. 请保持座位整洁，离开时请带走个人物品\n5. 禁止占座，如发现占座行为将记录违规\n\n感谢您的配合！', 'URGENT', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (4, '图书借阅规则', '图书借阅规则：\n\n1. 每位读者最多可借阅5本图书\n2. 借阅期限为30天，可续借一次（15天）\n3. 逾期未还图书将产生积分扣除\n4. 请爱护图书，如有损坏需照价赔偿\n5. 热门图书可进行订阅，到货后会通知您\n\n祝您阅读愉快！', 'NORMAL', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (5, '图书馆维护通知', '图书馆将于本周六（1月18日）进行系统维护，维护时间为：\n\n维护时间：2026年1月18日 8:00 - 12:00\n\n维护期间，座位预约系统将暂停服务，给您带来的不便敬请谅解。\n\n维护完成后，系统将恢复正常使用。', 'CLOSURE', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (6, '积分系统说明', '积分系统说明：\n\n1. 按时签到可获得积分奖励\n2. 按时签退可获得积分奖励\n3. 违规行为（占座、迟到等）将扣除积分\n4. 积分可用于兑换图书借阅时长延长等福利\n5. 每月积分排行榜前10名将获得额外奖励\n\n请遵守规则，共同维护良好的学习环境！', 'NORMAL', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (7, '协同预约功能上线', '好消息！协同预约功能已正式上线！\n\n现在您可以：\n1. 创建学习小组\n2. 邀请好友一起预约相邻座位\n3. 协同学习，提高效率\n\n快来体验新功能吧！', 'URGENT', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');
INSERT INTO `notice` VALUES (8, '春节假期闭馆通知', '春节假期闭馆通知：\n\n图书馆将于以下时间闭馆：\n2026年1月28日（除夕）至2026年2月3日（初六）\n\n2月4日（初七）起正常开放。\n\n祝大家春节快乐，新年进步！', 'CLOSURE', 0, 1, NULL, NULL, '2026-01-13 16:44:48', '2026-01-13 16:44:48');

-- ----------------------------
-- Table structure for reservation
-- ----------------------------
DROP TABLE IF EXISTS `reservation`;
CREATE TABLE `reservation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `seat_id` bigint(20) NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` enum('ACTIVE','CANCELLED','FINISHED','PENDING','CONFIRMED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `check_in_time` datetime NULL DEFAULT NULL,
  `check_out_time` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_seat_id`(`seat_id` ASC) USING BTREE,
  INDEX `idx_start_time`(`start_time` ASC) USING BTREE,
  INDEX `idx_end_time`(`end_time` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_reservation_seat` FOREIGN KEY (`seat_id`) REFERENCES `seat` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 30 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of reservation
-- ----------------------------

-- ----------------------------
-- Table structure for seat
-- ----------------------------
DROP TABLE IF EXISTS `seat`;
CREATE TABLE `seat`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `building` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '楼栋名称，如：A楼',
  `floor` int(11) NULL DEFAULT NULL COMMENT '楼层号',
  `label` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '座位标签，如：A101',
  `row_num` int(11) NULL DEFAULT NULL COMMENT '行号，用于CSS Grid布局',
  `col_num` int(11) NULL DEFAULT NULL COMMENT '列号，用于CSS Grid布局',
  `area` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '区域名称，如：一楼自习区、二楼自习区等',
  `has_power` tinyint(1) NULL DEFAULT 0 COMMENT '是否有电源',
  `is_window` tinyint(1) NULL DEFAULT 0 COMMENT '是否靠窗',
  `zone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '区域类型：安静区、自习区等',
  `status` enum('FREE','IDLE','RESERVED','OCCUPIED','BROKEN','FAULT') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FREE' COMMENT '座位状态',
  `note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_building_floor`(`building` ASC, `floor` ASC) USING BTREE,
  INDEX `idx_zone`(`zone` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_area`(`area` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of seat
-- ----------------------------
INSERT INTO `seat` VALUES (1, 'A楼', 1, 'A1-001', 1, 1, '一楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-04-24 15:47:37');
INSERT INTO `seat` VALUES (2, 'A楼', 1, 'A1-002', 1, 2, '一楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-04-18 00:19:28');
INSERT INTO `seat` VALUES (3, 'A楼', 1, 'A1-003', 1, 3, '一楼安静区', 0, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (4, 'A楼', 1, 'A1-004', 1, 4, '一楼自习区', 1, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (5, 'A楼', 1, 'A1-005', 1, 5, '一楼自习区', 0, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (6, 'A楼', 1, 'A1-006', 1, 6, '一楼自习区', 1, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (7, 'A楼', 1, 'A1-007', 2, 1, '一楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (8, 'A楼', 1, 'A1-008', 2, 2, '一楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (9, 'A楼', 1, 'A1-009', 2, 3, '一楼安静区', 0, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (10, 'A楼', 1, 'A1-010', 2, 4, '一楼自习区', 1, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (11, 'A楼', 1, 'A1-011', 2, 5, '一楼自习区', 0, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (12, 'A楼', 1, 'A1-012', 2, 6, '一楼自习区', 0, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (13, 'A楼', 1, 'A1-013', 3, 1, '一楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (14, 'A楼', 1, 'A1-014', 3, 2, '一楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (15, 'A楼', 1, 'A1-015', 3, 3, '一楼安静区', 0, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (16, 'A楼', 1, 'A1-016', 3, 4, '一楼自习区', 1, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (17, 'A楼', 1, 'A1-017', 3, 5, '一楼自习区', 0, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (18, 'A楼', 1, 'A1-018', 3, 6, '一楼自习区', 0, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (19, 'A楼', 2, 'A2-001', 1, 1, '二楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (20, 'A楼', 2, 'A2-002', 1, 2, '二楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (21, 'A楼', 2, 'A2-003', 1, 3, '二楼安静区', 0, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (22, 'A楼', 2, 'A2-004', 1, 4, '二楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (23, 'A楼', 2, 'A2-005', 1, 5, '二楼自习区', 1, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (24, 'A楼', 2, 'A2-006', 1, 6, '二楼自习区', 0, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (25, 'A楼', 2, 'A2-007', 1, 7, '二楼自习区', 1, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (26, 'A楼', 2, 'A2-008', 1, 8, '二楼自习区', 0, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (27, 'A楼', 2, 'A2-009', 2, 1, '二楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (28, 'A楼', 2, 'A2-010', 2, 2, '二楼安静区', 1, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (29, 'A楼', 2, 'A2-011', 2, 3, '二楼安静区', 0, 1, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (30, 'A楼', 2, 'A2-012', 2, 4, '二楼安静区', 1, 0, '安静区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (31, 'A楼', 2, 'A2-013', 2, 5, '二楼自习区', 1, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (32, 'A楼', 2, 'A2-014', 2, 6, '二楼自习区', 0, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (33, 'A楼', 2, 'A2-015', 2, 7, '二楼自习区', 1, 0, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (34, 'A楼', 2, 'A2-016', 2, 8, '二楼自习区', 0, 1, '自习区', 'FREE', NULL, '2026-01-13 13:36:16', '2026-01-13 13:36:16');
INSERT INTO `seat` VALUES (35, 'A楼', 1, 'A1-020', 4, 1, '一楼自习区', 0, 1, '自习区', 'FREE', '靠窗无电源', '2026-04-15 17:31:33', '2026-04-15 17:33:19');
INSERT INTO `seat` VALUES (36, 'A楼', 1, 'A1-019', 4, 1, '一楼安静区', 1, 1, '安静区', 'FREE', '靠窗有电源', '2026-04-15 17:33:50', '2026-04-15 17:34:15');

-- ----------------------------
-- Table structure for seat_book_link
-- ----------------------------
DROP TABLE IF EXISTS `seat_book_link`;
CREATE TABLE `seat_book_link`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint(20) NOT NULL,
  `book_id` bigint(20) NOT NULL,
  `place_status` enum('TO_PLACE','PLACED','CONFIRMED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TO_PLACE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_sbl_res`(`reservation_id` ASC) USING BTREE,
  INDEX `fk_sbl_book`(`book_id` ASC) USING BTREE,
  CONSTRAINT `fk_sbl_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_sbl_res` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of seat_book_link
-- ----------------------------

-- ----------------------------
-- Table structure for study_group
-- ----------------------------
DROP TABLE IF EXISTS `study_group`;
CREATE TABLE `study_group`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `leader_id` bigint(20) NOT NULL,
  `is_published` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已发布，0=未发布，1=已发布',
  `reservation_start_time` datetime NULL DEFAULT NULL COMMENT '预约起始时间，只有到达这个时间后其他用户才能申请加入',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_group_leader`(`leader_id` ASC) USING BTREE,
  CONSTRAINT `fk_group_leader` FOREIGN KEY (`leader_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of study_group
-- ----------------------------

-- ----------------------------
-- Table structure for subscription
-- ----------------------------
DROP TABLE IF EXISTS `subscription`;
CREATE TABLE `subscription`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `book_id` bigint(20) NOT NULL COMMENT '图书ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_book_subscription`(`user_id` ASC, `book_id` ASC) USING BTREE COMMENT '用户和图书的唯一组合，防止重复订阅',
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_book_id`(`book_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  CONSTRAINT `fk_subscription_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_subscription_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of subscription
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('USER','ADMIN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
  `is_frozen` tinyint(1) NOT NULL DEFAULT 0,
  `is_blacklisted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否在黑名单中（1=是，0=否）',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` tinyint(4) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE,
  UNIQUE INDEX `phone`(`phone` ASC) USING BTREE,
  INDEX `idx_is_blacklisted`(`is_blacklisted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'admin', NULL, NULL, '$2a$10$iq2neE4fxvgUUsam/m2SC.wU5uXDN5hImWPYDZ0DvBwDc6GfjZRiy', 'ADMIN', 0, 0, '/uploads/avatars/f7a82489-36c7-4887-a4a2-5558d86469d9.jpg', 1, '2026-01-13 13:36:35', '2026-01-13 13:36:35');

-- ----------------------------
-- Table structure for user_notification
-- ----------------------------
DROP TABLE IF EXISTS `user_notification`;
CREATE TABLE `user_notification`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型: BOOK_AVAILABLE（图书上架通知）等',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容',
  `related_book_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的图书ID（如果是图书相关通知）',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_is_read`(`is_read` ASC) USING BTREE,
  INDEX `idx_user_read`(`user_id` ASC, `is_read` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `fk_notification_book`(`related_book_id` ASC) USING BTREE,
  CONSTRAINT `fk_notification_book` FOREIGN KEY (`related_book_id`) REFERENCES `book` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_notification
-- ----------------------------

-- ----------------------------
-- Table structure for violation
-- ----------------------------
DROP TABLE IF EXISTS `violation`;
CREATE TABLE `violation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `reservation_id` bigint(20) NULL DEFAULT NULL,
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '违规类型: LATE_CHECKIN, NO_SHOW, OVERTIME, OTHER',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '违规描述',
  `occurred_at` datetime NOT NULL COMMENT '违规发生时间',
  `handled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已处理',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `fk_violation_reservation`(`reservation_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_violation_occurred_at`(`occurred_at` ASC) USING BTREE,
  CONSTRAINT `fk_violation_reservation` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_violation_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of violation
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
