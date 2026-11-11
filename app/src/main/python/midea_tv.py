from midea_beautiful import connect_to_cloud
from midea_beautiful.lan import LanDevice

APPLIANCE_ID = "153931629834823"

cloud = None
device = None


def login(account, password):
    global cloud, device

    cloud = connect_to_cloud(
        account=account,
        password=password
    )

    appliances = cloud.list_appliances(force=True)

    appliance = next(
        x for x in appliances
        if str(x.get("id")) == APPLIANCE_ID
    )

    device = LanDevice(
        appliance_id=APPLIANCE_ID,
        appliance_type=str(appliance.get("type"))
    )

    device.refresh(cloud)

    return "Sikeres Midea bejelentkezés!"


def get_temperature():
    if cloud is None or device is None:
        raise RuntimeError("Először jelentkezz be!")

    device.refresh(cloud)

    raw = device.state.latest_data

    if raw is None or len(raw) <= 15:
        raise RuntimeError("Érvénytelen Midea hőmérsékleti adat!")

    whole = (raw[12] - 50) / 2
    decimal = ((raw[15] >> 4) & 0x0F) / 10

    if whole < 0:
        original = whole - decimal
    else:
        original = whole + decimal

    return round(original, 1)
