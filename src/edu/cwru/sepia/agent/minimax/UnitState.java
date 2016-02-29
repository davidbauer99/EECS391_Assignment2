package edu.cwru.sepia.agent.minimax;

public class UnitState {

	private int xPos;
	private int yPos;
	private int health;
	private int damage;
	private int range;
	private int id;

	public UnitState(int xPos, int yPos, int health, int damage, int range,
			int id) {
		super();
		this.xPos = xPos;
		this.yPos = yPos;
		this.health = health;
		this.damage = damage;
		this.range = range;
		this.id = id;
	}

	public int getXPos() {
		return xPos;
	}

	public void setXPos(int xPos) {
		this.xPos = xPos;
	}

	public int getYPos() {
		return yPos;
	}

	public void setYPos(int yPos) {
		this.yPos = yPos;
	}

	public int getHealth() {
		return health;
	}

	public void setHealth(int health) {
		this.health = health;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getDamage() {
		return damage;
	}

	public void setDamage(int damage) {
		this.damage = damage;
	}

	public int getRange() {
		return range;
	}

	public void setRange(int range) {
		this.range = range;
	}

}
