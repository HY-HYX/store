import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BATTLE extends Application {

    // ----- 窗口常量 -----
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // ----- 游戏状态 -----
    private enum GameState { READY, FIGHTING, GAME_OVER }
    private GameState state = GameState.READY;

    // ----- 游戏实体 -----
    private Fighter player;
    private Fighter enemy;
    private List<Bullet> bullets;       // 飞行中的远程子弹
    private List<AOE> aoes;            // 地面持续范围技能
    private List<Particle> particles;  // 受击特效

    // ----- 输入控制 -----
    private boolean up, down, left, right;
    private boolean skill1, skill2, skill3; // J/K/L

    // ----- 随机数 -----
    private Random random = new Random();

    // ----- 计时器（用于AI决策）-----
    private double aiTimer = 0;

    // ----- Canvas 与画笔 -----
    private Canvas canvas;
    private GraphicsContext gc;

    // ==================== 内部类 ====================

    /** 战斗单位（玩家或敌人） */
    static class Fighter {
        double x, y;          // 坐标（圆心）
        double radius = 18;   // 碰撞半径
        double speed = 200;   // 移动速度
        int maxHp = 100;
        int hp;
        double facingAngle = 0; // 朝向弧度（右为0，顺时针）
        // 三个技能的冷却计时器（秒）
        double cooldown1 = 0, cooldown2 = 0, cooldown3 = 0;
        // 技能冷却时间（秒）
        double cd1 = 0.5, cd2 = 2.0, cd3 = 5.0;
        boolean isPlayer;

        Fighter(double x, double y, boolean isPlayer) {
            this.x = x;
            this.y = y;
            this.isPlayer = isPlayer;
            this.hp = maxHp;
        }

        void resetCooldowns() {
            cooldown1 = 0; cooldown2 = 0; cooldown3 = 0;
        }

        void updateCooldowns(double dt) {
            if (cooldown1 > 0) cooldown1 = Math.max(0, cooldown1 - dt);
            if (cooldown2 > 0) cooldown2 = Math.max(0, cooldown2 - dt);
            if (cooldown3 > 0) cooldown3 = Math.max(0, cooldown3 - dt);
        }
    }

    /** 远程子弹 */
    static class Bullet {
        double x, y, vx, vy;
        double radius = 5;
        int damage;
        boolean fromPlayer; // 归属（避免自己打自己）
        double life = 3.0;  // 飞行时间，过期消失

        Bullet(double x, double y, double angle, double speed, int damage, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.damage = damage;
            this.fromPlayer = fromPlayer;
        }
    }

    /** 范围攻击效果（圆形AOE） */
    static class AOE {
        double x, y;
        double radius;
        double duration = 0.3; // 持续时间
        double timeLeft;
        int damage;
        boolean fromPlayer;

        AOE(double x, double y, double radius, int damage, boolean fromPlayer) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.damage = damage;
            this.fromPlayer = fromPlayer;
            this.timeLeft = duration;
        }
    }

    /** 受击粒子 */
    static class Particle {
        double x, y, vx, vy, life;
        Color color;
        double size;

        Particle(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            double angle = Math.random() * 2 * Math.PI;
            double speed = 50 + Math.random() * 150;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;
            life = 0.5 + Math.random() * 0.5;
            size = 3 + Math.random() * 4;
        }
    }

    // ==================== 应用入口 ====================
    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        // 键盘事件
        scene.setOnKeyPressed(e -> handleKey(e.getCode(), true));
        scene.setOnKeyReleased(e -> handleKey(e.getCode(), false));

        primaryStage.setTitle("Plane Battle - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        startGame();

        // 游戏循环
        new AnimationTimer() {
            private long lastTime = 0;
            @Override
            public void handle(long now) {
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double dt = (now - lastTime) / 1_000_000_000.0;
                if (dt > 0.1) dt = 0.1; // 防止卡顿
                lastTime = now;

                update(dt);
                render();
            }
        }.start();
    }

    private void handleKey(KeyCode code, boolean pressed) {
        switch (code) {
            case W: up = pressed; break;
            case A: left = pressed; break;
            case S: down = pressed; break;
            case D: right = pressed; break;
            case J: if (pressed && state == GameState.FIGHTING) skill1 = true; break;
            case K: if (pressed && state == GameState.FIGHTING) skill2 = true; break;
            case L: if (pressed && state == GameState.FIGHTING) skill3 = true; break;
            case SPACE:
                if (pressed) {
                    if (state == GameState.READY) state = GameState.FIGHTING;
                    else if (state == GameState.GAME_OVER) startGame();
                }
                break;
        }
    }

    private void startGame() {
        state = GameState.READY;
        player = new Fighter(200, 300, true);
        enemy = new Fighter(600, 300, false);
        player.resetCooldowns();
        enemy.resetCooldowns();
        bullets = new ArrayList<>();
        aoes = new ArrayList<>();
        particles = new ArrayList<>();
        up = down = left = right = false;
        skill1 = skill2 = skill3 = false;
    }

    // ==================== 核心更新 ====================
    private void update(double dt) {
        if (state != GameState.FIGHTING) return;

        updatePlayerMovement(dt);
        updateEnemyAI(dt);

        // 技能输入（玩家）
        if (skill1 && player.cooldown1 <= 0) {
            performSkill(player, 1);
            player.cooldown1 = player.cd1;
            skill1 = false;
        }
        if (skill2 && player.cooldown2 <= 0) {
            performSkill(player, 2);
            player.cooldown2 = player.cd2;
            skill2 = false;
        }
        if (skill3 && player.cooldown3 <= 0) {
            performSkill(player, 3);
            player.cooldown3 = player.cd3;
            skill3 = false;
        }

        // 更新冷却
        player.updateCooldowns(dt);
        enemy.updateCooldowns(dt);

        // 子弹移动与碰撞
        Iterator<Bullet> bIt = bullets.iterator();
        while (bIt.hasNext()) {
            Bullet b = bIt.next();
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.life -= dt;

            // 边界消失
            if (b.life <= 0 || b.x < 0 || b.y < 0 || b.x > WIDTH || b.y > HEIGHT) {
                bIt.remove();
                continue;
            }

            // 碰撞检测：对目标
            Fighter target = b.fromPlayer ? enemy : player;
            if (target != null && distance(b.x, b.y, target.x, target.y) < (b.radius + target.radius)) {
                damageFighter(target, b.damage);
                spawnHitParticles(b.x, b.y);
                bIt.remove();
            }
        }

        // AOE 效果更新与伤害
        Iterator<AOE> aoeIt = aoes.iterator();
        while (aoeIt.hasNext()) {
            AOE a = aoeIt.next();
            a.timeLeft -= dt;
            if (a.timeLeft <= 0) {
                aoeIt.remove();
                continue;
            }

            // 每帧对范围内的敌人造成伤害？为了避免持续伤害过于暴力，只在创建时造成一次伤害，这里不重复伤害。
            // （伤害已在 performSkill 中施加）
        }

        // 粒子更新
        for (Particle p : particles) {
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            // 简单阻力
            p.vx *= 0.98;
            p.vy *= 0.98;
        }
        particles.removeIf(p -> p.life <= 0);

        // 检查胜负
        if (player.hp <= 0 || enemy.hp <= 0) {
            state = GameState.GAME_OVER;
        }
    }

    // 玩家移动（WASD）
    private void updatePlayerMovement(double dt) {
        double dx = 0, dy = 0;
        if (left) dx -= 1;
        if (right) dx += 1;
        if (up) dy -= 1;
        if (down) dy += 1;
        if (dx != 0 || dy != 0) {
            double len = Math.sqrt(dx*dx + dy*dy);
            dx /= len;
            dy /= len;
            player.facingAngle = Math.atan2(dy, dx);
        }
        player.x += dx * player.speed * dt;
        player.y += dy * player.speed * dt;
        clampPosition(player);
    }

    // 简单 AI：随机移动 + 追踪 + 使用技能
    private void updateEnemyAI(double dt) {
        aiTimer -= dt;
        if (aiTimer <= 0) {
            aiTimer = 0.5 + random.nextDouble() * 1.0; // 0.5~1.5秒重新决策
            // 随机方向
            double angle = random.nextDouble() * 2 * Math.PI;
            // 有一定概率朝向玩家
            if (random.nextDouble() < 0.4) {
                angle = Math.atan2(player.y - enemy.y, player.x - enemy.x);
            }
            enemy.facingAngle = angle;
        }

        // 移动
        double dx = Math.cos(enemy.facingAngle);
        double dy = Math.sin(enemy.facingAngle);
        enemy.x += dx * enemy.speed * dt;
        enemy.y += dy * enemy.speed * dt;
        clampPosition(enemy);

        // AI 使用技能
        double distToPlayer = distance(enemy.x, enemy.y, player.x, player.y);
        if (distToPlayer < 80 && enemy.cooldown1 <= 0) {
            performSkill(enemy, 1);
            enemy.cooldown1 = enemy.cd1;
        } else if (distToPlayer < 350 && enemy.cooldown2 <= 0) {
            performSkill(enemy, 2);
            enemy.cooldown2 = enemy.cd2;
        } else if (distToPlayer < 250 && enemy.cooldown3 <= 0) {
            performSkill(enemy, 3);
            enemy.cooldown3 = enemy.cd3;
        }
    }

    // 技能执行
    private void performSkill(Fighter caster, int skillNum) {
        switch (skillNum) {
            case 1: // 近战扇形攻击
                double range1 = 70;
                double halfAngle = Math.toRadians(40); // 扇形半角
                // 对范围内的敌人造成伤害
                Fighter target = caster.isPlayer ? enemy : player;
                if (isInSector(target.x, target.y, caster.x, caster.y, caster.facingAngle, range1, halfAngle)) {
                    damageFighter(target, 15);
                }
                // 视觉效果：扇形粒子
                for (int i = 0; i < 12; i++) {
                    double a = caster.facingAngle - halfAngle + (2 * halfAngle) * i / 11;
                    double dist = range1 * (0.5 + 0.5 * Math.random());
                    particles.add(new Particle(caster.x + Math.cos(a) * dist, caster.y + Math.sin(a) * dist, Color.YELLOW));
                }
                break;

            case 2: // 远程子弹
                double bulletSpeed = 350;
                Bullet b = new Bullet(caster.x, caster.y, caster.facingAngle, bulletSpeed, 20, caster.isPlayer);
                bullets.add(b);
                break;

            case 3: // 环形 AOE
                double aoeRadius = 120;
                aoes.add(new AOE(caster.x, caster.y, aoeRadius, 25, caster.isPlayer));
                // 立即对范围内敌人造成伤害
                Fighter targetAOE = caster.isPlayer ? enemy : player;
                if (distance(caster.x, caster.y, targetAOE.x, targetAOE.y) < aoeRadius) {
                    damageFighter(targetAOE, 25);
                }
                // 粒子效果
                for (int i = 0; i < 20; i++) {
                    double a = Math.random() * 2 * Math.PI;
                    double r = Math.random() * aoeRadius;
                    particles.add(new Particle(caster.x + Math.cos(a)*r, caster.y + Math.sin(a)*r, Color.CYAN));
                }
                break;
        }
    }

    // 对 Fighter 造成伤害
    private void damageFighter(Fighter f, int damage) {
        f.hp = Math.max(0, f.hp - damage);
        spawnHitParticles(f.x, f.y);
    }

    // 生成受击粒子
    private void spawnHitParticles(double x, double y) {
        for (int i = 0; i < 6; i++) {
            particles.add(new Particle(x, y, Color.RED));
        }
    }

    // 工具：两点距离
    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return Math.sqrt(dx*dx + dy*dy);
    }

    // 判断点是否在扇形区域内
    private boolean isInSector(double px, double py, double cx, double cy, double facingAngle, double range, double halfAngle) {
        double dx = px - cx;
        double dy = py - cy;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist > range) return false;
        double angle = Math.atan2(dy, dx);
        // 角度差值处理（归一化到 -PI ~ PI）
        double diff = angle - facingAngle;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        return Math.abs(diff) <= halfAngle;
    }

    // 限制单位在窗口内
    private void clampPosition(Fighter f) {
        f.x = Math.max(f.radius, Math.min(WIDTH - f.radius, f.x));
        f.y = Math.max(f.radius, Math.min(HEIGHT - f.radius, f.y));
    }

    // ==================== 渲染 ====================
    private void render() {
        // 背景
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // 绘制格子线（装饰）
        gc.setStroke(Color.DARKGRAY);
        for (int i = 0; i < WIDTH; i += 50) {
            gc.strokeLine(i, 0, i, HEIGHT);
        }
        for (int j = 0; j < HEIGHT; j += 50) {
            gc.strokeLine(0, j, WIDTH, j);
        }

        if (state == GameState.READY) {
            drawCenteredText("Press SPACE to start", HEIGHT/2, 28);
        }

        // 绘制 AOE 范围（半透明圆）
        for (AOE a : aoes) {
            double alpha = a.timeLeft / a.duration * 0.3;
            gc.setFill(new Color(0, 1, 1, alpha));
            gc.fillOval(a.x - a.radius, a.y - a.radius, a.radius*2, a.radius*2);
            gc.setStroke(new Color(0, 1, 1, alpha + 0.2));
            gc.strokeOval(a.x - a.radius, a.y - a.radius, a.radius*2, a.radius*2);
        }

        // 绘制子弹
        for (Bullet b : bullets) {
            gc.setFill(b.fromPlayer ? Color.LIGHTBLUE : Color.ORANGERED);
            gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius*2, b.radius*2);
        }

        // 绘制 Fighter
        drawFighter(player, Color.ROYALBLUE, Color.CORNFLOWERBLUE);
        drawFighter(enemy, Color.CRIMSON, Color.INDIANRED);

        // 绘制粒子
        for (Particle p : particles) {
            double alpha = Math.min(1, p.life / 0.5);
            gc.setFill(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
            gc.fillOval(p.x - p.size/2, p.y - p.size/2, p.size, p.size);
        }

        // UI 血条与技能冷却
        drawUI();

        if (state == GameState.GAME_OVER) {
            String winner = player.hp > 0 ? "YOU WIN!" : "YOU LOSE!";
            drawCenteredText(winner, HEIGHT/2, 40);
            drawCenteredText("Press SPACE to restart", HEIGHT/2 + 50, 22);
        }
    }

    private void drawFighter(Fighter f, Color body, Color border) {
        // 身体
        gc.setFill(body);
        gc.fillOval(f.x - f.radius, f.y - f.radius, f.radius*2, f.radius*2);
        // 边框
        gc.setStroke(border);
        gc.setLineWidth(2);
        gc.strokeOval(f.x - f.radius, f.y - f.radius, f.radius*2, f.radius*2);

        // 朝向指示器（小三角形）
        double ax = f.x + Math.cos(f.facingAngle) * f.radius;
        double ay = f.y + Math.sin(f.facingAngle) * f.radius;
        gc.setFill(Color.WHITE);
        double[] triX = {
                ax,
                f.x + Math.cos(f.facingAngle + 2.5) * 10,
                f.x + Math.cos(f.facingAngle - 2.5) * 10
        };
        double[] triY = {
                ay,
                f.y + Math.sin(f.facingAngle + 2.5) * 10,
                f.y + Math.sin(f.facingAngle - 2.5) * 10
        };
        gc.fillPolygon(triX, triY, 3);
    }

    private void drawUI() {
        // 玩家血条（左下）
        drawHealthBar(20, HEIGHT - 40, 150, player.hp, player.maxHp, Color.GREEN);
        // 敌人血条（右下）
        drawHealthBar(WIDTH - 20 - 150, HEIGHT - 40, 150, enemy.hp, enemy.maxHp, Color.RED);

        // 技能冷却图标（左下角）
        gc.setFill(Color.WHITE);
        gc.setFont(new Font(12));
        double iconX = 20;
        double iconY = HEIGHT - 70;
        double iconSize = 30;

        // 技能1 近战
        drawCooldownIcon(iconX, iconY, iconSize, player.cooldown1, player.cd1, "J");
        // 技能2 子弹
        drawCooldownIcon(iconX + 40, iconY, iconSize, player.cooldown2, player.cd2, "K");
        // 技能3 AOE
        drawCooldownIcon(iconX + 80, iconY, iconSize, player.cooldown3, player.cd3, "L");

        // 操作提示
        gc.setFill(Color.LIGHTGRAY);
        gc.fillText("WASD: Move", 20, 20);
        gc.fillText("J: Melee  K: Shoot  L: AOE", 20, 35);
        gc.fillText("SPACE: Start/Restart", 20, 50);
    }

    private void drawHealthBar(double x, double y, double width, int hp, int maxHp, Color color) {
        double ratio = (double) hp / maxHp;
        gc.setStroke(Color.WHITE);
        gc.strokeRect(x, y, width, 15);
        gc.setFill(Color.GRAY);
        gc.fillRect(x, y, width, 15);
        gc.setFill(color);
        gc.fillRect(x, y, width * ratio, 15);
        gc.setFill(Color.WHITE);
        gc.setFont(new Font(12));
        gc.fillText(hp + " / " + maxHp, x + 5, y + 12);
    }

    private void drawCooldownIcon(double x, double y, double size, double currentCD, double maxCD, String key) {
        gc.setStroke(Color.WHITE);
        gc.strokeRect(x, y, size, size);
        double ratio = currentCD / maxCD;
        gc.setFill(new Color(0.3, 0.3, 0.3, 0.8));
        gc.fillRect(x, y, size, size * ratio);
        gc.setFill(Color.WHITE);
        gc.fillText(key, x + 5, y + size/2 + 5);
    }

    private void drawCenteredText(String text, double y, int size) {
        gc.setFill(Color.WHITE);
        gc.setFont(new Font(size));
        double textWidth = text.length() * size * 0.55; // 估算
        gc.fillText(text, WIDTH/2 - textWidth/2, y);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

