#include <SFML/Graphics.hpp>
#include <SFML/Window.hpp>
#include <SFML/System.hpp>
#include <iostream>
#include <vector>
#include <cmath>
#include <random>

// ---------- 常量 ----------
const int WIDTH = 800;
const int HEIGHT = 600;

// ---------- 工具函数 ----------
inline float distance(float x1, float y1, float x2, float y2) {
    float dx = x2 - x1, dy = y2 - y1;
    return std::sqrt(dx * dx + dy * dy);
}

// 角度差值归一化（-PI ~ PI）
inline float angleDiff(float a, float b) {
    float diff = a - b;
    while (diff > 3.14159265f) diff -= 2 * 3.14159265f;
    while (diff < -3.14159265f) diff += 2 * 3.14159265f;
    return diff;
}

// ---------- 结构体定义 ----------
struct Fighter {
    float x, y;
    float radius = 18;
    float speed = 200;
    int maxHp = 100, hp;
    float facingAngle = 0;      // 弧度
    float cd1 = 0, cd2 = 0, cd3 = 0;   // 当前冷却进度
    float c1 = 0.5f, c2 = 2.0f, c3 = 5.0f; // 冷却总时间
    bool isPlayer;

    Fighter(float x_, float y_, bool player) : x(x_), y(y_), isPlayer(player), hp(maxHp) {}

    void updateCooldowns(float dt) {
        cd1 = std::max(0.0f, cd1 - dt);
        cd2 = std::max(0.0f, cd2 - dt);
        cd3 = std::max(0.0f, cd3 - dt);
    }
};

struct Bullet {
    float x, y, vx, vy;
    float radius = 5;
    int damage = 20;
    bool fromPlayer;
    float life = 3.0f;

    Bullet(float sx, float sy, float angle, float speed, int dmg, bool player)
        : x(sx), y(sy), damage(dmg), fromPlayer(player) {
        vx = std::cos(angle) * speed;
        vy = std::sin(angle) * speed;
    }
};

struct AOE {
    float x, y, radius;
    float duration = 0.3f, timeLeft;
    int damage = 25;
    bool fromPlayer;

    AOE(float ax, float ay, float r, int dmg, bool player)
        : x(ax), y(ay), radius(r), damage(dmg), fromPlayer(player), timeLeft(duration) {
    }
};

struct Particle {
    float x, y, vx, vy, life, size;
    sf::Color color;

    Particle(float px, float py, sf::Color c) : x(px), y(py), color(c) {
        float angle = rand() % 360 / 180.0f * 3.14159265f;
        float speed = 50 + rand() % 150;
        vx = std::cos(angle) * speed;
        vy = std::sin(angle) * speed;
        life = 0.5f + (rand() % 500) / 1000.0f;
        size = 3 + rand() % 4;
    }
};

// ---------- 全局变量 ----------
sf::RenderWindow window(sf::VideoMode(WIDTH, HEIGHT), "Plane Battle - SFML");
Fighter player(200, 300, true);
Fighter enemy(600, 300, false);
std::vector<Bullet> bullets;
std::vector<AOE> aoes;
std::vector<Particle> particles;

enum class GameState { READY, FIGHTING, GAME_OVER };
GameState state = GameState::READY;


// 输入状态
bool keyW = false, keyA = false, keyS = false, keyD = false;
bool keyJ = false, keyK = false, keyL = false;
bool keySpace = false;

// AI 计时器
float aiTimer = 0;
std::mt19937 rng(std::random_device{}());

// 技能函数
void performSkill(Fighter& caster, int skillNum);
void damageFighter(Fighter& f, int damage);
void spawnHitParticles(float x, float y);
bool isInSector(float px, float py, float cx, float cy, float facing, float range, float halfAngle);
void clampPosition(Fighter& f);

// ---------- 初始化 ----------
void startGame() {
    player = Fighter(200, 300, true);
    enemy = Fighter(600, 300, false);
    player.cd1 = player.cd2 = player.cd3 = 0;
    enemy.cd1 = enemy.cd2 = enemy.cd3 = 0;
    bullets.clear();
    aoes.clear();
    particles.clear();
    state == GameState::READY;
}

// ---------- 主更新 ----------
void update(float dt) {
    if (state != GameState::FIGHTING) return;


    // 玩家移动
    float dx = 0, dy = 0;
    if (keyA) dx -= 1;
    if (keyD) dx += 1;
    if (keyW) dy -= 1;
    if (keyS) dy += 1;
    if (dx != 0 || dy != 0) {
        float len = std::sqrt(dx * dx + dy * dy);
        dx /= len; dy /= len;
        player.facingAngle = std::atan2(dy, dx);
    }
    player.x += dx * player.speed * dt;
    player.y += dy * player.speed * dt;
    clampPosition(player);

    // 玩家技能
    if (keyJ && player.cd1 <= 0) { performSkill(player, 1); player.cd1 = player.c1; }
    if (keyK && player.cd2 <= 0) { performSkill(player, 2); player.cd2 = player.c2; }
    if (keyL && player.cd3 <= 0) { performSkill(player, 3); player.cd3 = player.c3; }

    // 冷却更新
    player.updateCooldowns(dt);
    enemy.updateCooldowns(dt);

    // 敌人 AI
    aiTimer -= dt;
    if (aiTimer <= 0) {
        aiTimer = 0.5f + (rng() % 1000) / 1000.0f;
        // 随机朝向或追踪玩家
        float toPlayer = std::atan2(player.y - enemy.y, player.x - enemy.x);
        if (rng() % 100 < 40) enemy.facingAngle = toPlayer;
        else enemy.facingAngle = (rng() % 360) / 180.0f * 3.14159265f;
    }
    float edx = std::cos(enemy.facingAngle), edy = std::sin(enemy.facingAngle);
    enemy.x += edx * enemy.speed * dt;
    enemy.y += edy * enemy.speed * dt;
    clampPosition(enemy);

    float distToPlayer = distance(enemy.x, enemy.y, player.x, player.y);
    if (distToPlayer < 80 && enemy.cd1 <= 0) {
        performSkill(enemy, 1);
        enemy.cd1 = enemy.c1;
    }
    else if (distToPlayer < 350 && enemy.cd2 <= 0) {
        performSkill(enemy, 2);
        enemy.cd2 = enemy.c2;
    }
    else if (distToPlayer < 250 && enemy.cd3 <= 0) {
        performSkill(enemy, 3);
        enemy.cd3 = enemy.c3;
    }

    // 更新子弹
    for (auto& b : bullets) {
        b.x += b.vx * dt;
        b.y += b.vy * dt;
        b.life -= dt;
    }
    bullets.erase(std::remove_if(bullets.begin(), bullets.end(), [](Bullet& b) {
        if (b.life <= 0 || b.x < 0 || b.y < 0 || b.x > WIDTH || b.y > HEIGHT) return true;
        return false;
        }), bullets.end());

    // 子弹碰撞检测
    for (auto& b : bullets) {
        Fighter* target = b.fromPlayer ? &enemy : &player;
        if (distance(b.x, b.y, target->x, target->y) < b.radius + target->radius) {
            damageFighter(*target, b.damage);
            spawnHitParticles(b.x, b.y);
            b.life = -1; // 标记删除
        }
    }
    bullets.erase(std::remove_if(bullets.begin(), bullets.end(), [](const Bullet& b) { return b.life < 0; }), bullets.end());

    // 更新 AOE
    for (auto& a : aoes) a.timeLeft -= dt;
    aoes.erase(std::remove_if(aoes.begin(), aoes.end(), [](const AOE& a) { return a.timeLeft <= 0; }), aoes.end());

    // 更新粒子
    for (auto& p : particles) {
        p.life -= dt;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.vx *= 0.98f;
        p.vy *= 0.98f;
    }
    particles.erase(std::remove_if(particles.begin(), particles.end(), [](const Particle& p) { return p.life <= 0; }), particles.end());

    // 胜负判定
    if (player.hp <= 0 || enemy.hp <= 0) state = GameState::GAME_OVER;

}

// ---------- 技能实现 ----------
void performSkill(Fighter& caster, int num) {
    switch (num) {
    case 1: { // 近战扇形
        float range = 70, halfAngle = 40.0f / 180.0f * 3.14159265f;
        Fighter* target = caster.isPlayer ? &enemy : &player;
        if (isInSector(target->x, target->y, caster.x, caster.y, caster.facingAngle, range, halfAngle))
            damageFighter(*target, 15);
        // 粒子
        for (int i = 0; i < 12; i++) {
            float a = caster.facingAngle - halfAngle + (2 * halfAngle) * i / 11;
            float dist = range * (0.5f + (rng() % 1000) / 2000.0f);
            particles.emplace_back(caster.x + std::cos(a) * dist, caster.y + std::sin(a) * dist, sf::Color::Yellow);
        }
        break;
    }
    case 2: { // 子弹
        bullets.emplace_back(caster.x, caster.y, caster.facingAngle, 350.0f, 20, caster.isPlayer);
        break;
    }
    case 3: { // AOE
        float aoeRadius = 120;
        aoes.emplace_back(caster.x, caster.y, aoeRadius, 25, caster.isPlayer);
        Fighter* target = caster.isPlayer ? &enemy : &player;
        if (distance(caster.x, caster.y, target->x, target->y) < aoeRadius)
            damageFighter(*target, 25);
        for (int i = 0; i < 20; i++) {
            float a = (rng() % 360) / 180.0f * 3.14159265f;
            float r = (rng() % 100) / 100.0f * aoeRadius;
            particles.emplace_back(caster.x + std::cos(a) * r, caster.y + std::sin(a) * r, sf::Color::Cyan);
        }
        break;
    }
    }
}

void damageFighter(Fighter& f, int damage) {
    f.hp = std::max(0, f.hp - damage);
    spawnHitParticles(f.x, f.y);
}

void spawnHitParticles(float x, float y) {
    for (int i = 0; i < 6; i++)
        particles.emplace_back(x, y, sf::Color::Red);
}

bool isInSector(float px, float py, float cx, float cy, float facing, float range, float halfAngle) {
    float dx = px - cx, dy = py - cy;
    float dist = std::sqrt(dx * dx + dy * dy);
    if (dist > range) return false;
    float angle = std::atan2(dy, dx);
    float diff = angle - facing;
    while (diff > 3.14159265f) diff -= 2 * 3.14159265f;
    while (diff < -3.14159265f) diff += 2 * 3.14159265f;
    return std::abs(diff) <= halfAngle;
}

void clampPosition(Fighter& f) {
    f.x = std::max(f.radius, std::min((float)WIDTH - f.radius, f.x));
    f.y = std::max(f.radius, std::min((float)HEIGHT - f.radius, f.y));
}

// ---------- 渲染 ----------
void render() {
    window.clear(sf::Color::Black);

    // 网格背景
    sf::VertexArray grid(sf::Lines);
    for (int x = 0; x <= WIDTH; x += 50) {
        grid.append(sf::Vertex(sf::Vector2f((float)x, 0), sf::Color(50, 50, 50)));
        grid.append(sf::Vertex(sf::Vector2f((float)x, HEIGHT), sf::Color(50, 50, 50)));
    }
    for (int y = 0; y <= HEIGHT; y += 50) {
        grid.append(sf::Vertex(sf::Vector2f(0, (float)y), sf::Color(50, 50, 50)));
        grid.append(sf::Vertex(sf::Vector2f(WIDTH, (float)y), sf::Color(50, 50, 50)));
    }
    window.draw(grid);

    // AOE 范围
    for (auto& a : aoes) {
        sf::CircleShape circle(a.radius);
        circle.setOrigin(a.radius, a.radius);
        circle.setPosition(a.x, a.y);
        circle.setFillColor(sf::Color(0, 255, 255, (sf::Uint8)(a.timeLeft / a.duration * 50)));
        circle.setOutlineThickness(2);
        circle.setOutlineColor(sf::Color(0, 255, 255, 100));
        window.draw(circle);
    }

    // 子弹
    sf::CircleShape bulletShape(5);
    bulletShape.setOrigin(5, 5);
    for (auto& b : bullets) {
        bulletShape.setPosition(b.x, b.y);
        bulletShape.setFillColor(b.fromPlayer ? sf::Color::Cyan : sf::Color(255, 100, 0));
        window.draw(bulletShape);
    }

    // 绘制 Fighter
    auto drawFighter = [&](Fighter& f, sf::Color body, sf::Color outline) {
        sf::CircleShape circle(f.radius);
        circle.setOrigin(f.radius, f.radius);
        circle.setPosition(f.x, f.y);
        circle.setFillColor(body);
        circle.setOutlineThickness(2);
        circle.setOutlineColor(outline);
        window.draw(circle);

        // 朝向指示器（小三角）
        sf::ConvexShape tri;
        tri.setPointCount(3);
        float tipX = f.x + std::cos(f.facingAngle) * f.radius;
        float tipY = f.y + std::sin(f.facingAngle) * f.radius;
        tri.setPoint(0, sf::Vector2f(tipX, tipY));
        tri.setPoint(1, sf::Vector2f(f.x + std::cos(f.facingAngle + 2.5f) * 10, f.y + std::sin(f.facingAngle + 2.5f) * 10));
        tri.setPoint(2, sf::Vector2f(f.x + std::cos(f.facingAngle - 2.5f) * 10, f.y + std::sin(f.facingAngle - 2.5f) * 10));
        tri.setFillColor(sf::Color::White);
        window.draw(tri);
        };
    drawFighter(player, sf::Color(50, 120, 200), sf::Color(100, 180, 255));
    drawFighter(enemy, sf::Color(180, 50, 50), sf::Color(255, 100, 100));

    // 粒子
    sf::CircleShape particleShape;
    particleShape.setOrigin(1, 1);
    for (auto& p : particles) {
        particleShape.setPosition(p.x, p.y);
        particleShape.setRadius(p.size / 2);
        sf::Uint8 alpha = (sf::Uint8)(std::min(1.0f, p.life / 0.5f) * 255);
        particleShape.setFillColor(sf::Color(p.color.r, p.color.g, p.color.b, alpha));
        window.draw(particleShape);
    }

    // UI 血条
    auto drawHealthBar = [&](float x, float y, float width, int hp, int maxHp, sf::Color color) {
        sf::RectangleShape bg(sf::Vector2f(width, 15));
        bg.setPosition(x, y);
        bg.setFillColor(sf::Color(100, 100, 100));
        window.draw(bg);
        sf::RectangleShape bar(sf::Vector2f(width * hp / maxHp, 15));
        bar.setPosition(x, y);
        bar.setFillColor(color);
        window.draw(bar);
        sf::RectangleShape border(sf::Vector2f(width, 15));
        border.setPosition(x, y);
        border.setFillColor(sf::Color::Transparent);
        border.setOutlineThickness(1);
        border.setOutlineColor(sf::Color::White);
        window.draw(border);
        };
    drawHealthBar(20, HEIGHT - 40, 150, player.hp, player.maxHp, sf::Color::Green);
    drawHealthBar(WIDTH - 170, HEIGHT - 40, 150, enemy.hp, enemy.maxHp, sf::Color::Red);

    // 技能冷却图标
    auto drawCDIcon = [&](float x, float y, float size, float cd, float maxCD, char key) {
        sf::RectangleShape icon(sf::Vector2f(size, size));
        icon.setPosition(x, y);
        icon.setFillColor(sf::Color(50, 50, 50));
        icon.setOutlineThickness(1);
        icon.setOutlineColor(sf::Color::White);
        window.draw(icon);
        if (cd > 0) {
            float ratio = cd / maxCD;
            sf::RectangleShape fill(sf::Vector2f(size, size * ratio));
            fill.setPosition(x, y);
            fill.setFillColor(sf::Color(200, 200, 200, 150));
            window.draw(fill);
        }
        sf::Text text;
        static sf::Font font;
        static bool fontLoaded = false;
        if (!fontLoaded) {
            font.loadFromFile("arial.ttf"); // 确保有字体文件，否则注释掉文本显示
            fontLoaded = true;
        }
        text.setFont(font);
        text.setCharacterSize(14);
        text.setFillColor(sf::Color::White);
        text.setString(key);
        text.setPosition(x + 8, y + 8);
        window.draw(text);
        };
    drawCDIcon(20, HEIGHT - 70, 30, player.cd1, player.c1, 'J');
    drawCDIcon(60, HEIGHT - 70, 30, player.cd2, player.c2, 'K');
    drawCDIcon(100, HEIGHT - 70, 30, player.cd3, player.c3, 'L');

    // 提示信息
    sf::Text info;
    static sf::Font infoFont;
    static bool fontLoaded2 = false;
    if (!fontLoaded2) { infoFont.loadFromFile("arial.ttf"); fontLoaded2 = true; }
    info.setFont(infoFont);
    info.setCharacterSize(14);
    info.setFillColor(sf::Color::White);
    info.setPosition(20, 20);
    info.setString("WASD: Move   J/K/L: Skills   Space: Action");
    window.draw(info);

    // 状态文字
    if (state == GameState::READY) {
        sf::Text readyText("Press SPACE to start", infoFont, 28);
        readyText.setFillColor(sf::Color::White);
        sf::FloatRect bounds = readyText.getLocalBounds();
        readyText.setPosition(WIDTH / 2 - bounds.width / 2, HEIGHT / 2 - 14);
        window.draw(readyText);
    }
    else if (state == GameState::GAME_OVER) {
        std::string winMsg = player.hp > 0 ? "YOU WIN!" : "YOU LOSE!";
        sf::Text overText(winMsg, infoFont, 40);
        overText.setFillColor(sf::Color::White);
        sf::FloatRect bounds = overText.getLocalBounds();
        overText.setPosition(WIDTH / 2 - bounds.width / 2, HEIGHT / 2 - 30);
        window.draw(overText);

        sf::Text restartText("Press SPACE to restart", infoFont, 22);
        restartText.setFillColor(sf::Color::White);
        bounds = restartText.getLocalBounds();
        restartText.setPosition(WIDTH / 2 - bounds.width / 2, HEIGHT / 2 + 20);
        window.draw(restartText);
    }

    window.display();
}

// ---------- 主函数 ----------
int main() {
    window.setFramerateLimit(60);
    startGame();

    sf::Clock clock;
    while (window.isOpen()) {
        sf::Event event;
        while (window.pollEvent(event)) {
            switch (event.type) {
            case sf::Event::Closed: window.close(); break;
            case sf::Event::KeyPressed:
                switch (event.key.code) {
                case sf::Keyboard::W: keyW = true; break;
                case sf::Keyboard::A: keyA = true; break;
                case sf::Keyboard::S: keyS = true; break;
                case sf::Keyboard::D: keyD = true; break;
                case sf::Keyboard::J: keyJ = true; break;
                case sf::Keyboard::K: keyK = true; break;
                case sf::Keyboard::L: keyL = true; break;
                case sf::Keyboard::Space: keySpace = true;
                    if (state == GameState::READY) state = GameState::FIGHTING;
                    else if (state == GameState::GAME_OVER) startGame();
                    break;
                }
                break;
            case sf::Event::KeyReleased:
                switch (event.key.code) {
                case sf::Keyboard::W: keyW = false; break;
                case sf::Keyboard::A: keyA = false; break;
                case sf::Keyboard::S: keyS = false; break;
                case sf::Keyboard::D: keyD = false; break;
                case sf::Keyboard::J: keyJ = false; break;
                case sf::Keyboard::K: keyK = false; break;
                case sf::Keyboard::L: keyL = false; break;
                case sf::Keyboard::Space: keySpace = false; break;
                }
                break;
            }
        }

        float dt = clock.restart().asSeconds();
        if (dt > 0.1f) dt = 0.1f; // 防止卡顿

        update(dt);
        render();
    }

    return 0;
}
