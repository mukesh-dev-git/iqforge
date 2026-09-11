"""Real application edited through the IQForge bridge to verify IQForge's mobile-to-laptop edit flow."""


def add(left: int, right: int) -> int:
    return left + right


def subtract(left: int, right: int) -> int:
    return left - right


if __name__ == "__main__":
    print(f"IQForge demo: 7 + 5 = {add(7, 5)}")
    print(f"IQForge demo: 7 - 5 = {subtract(7, 5)}")
