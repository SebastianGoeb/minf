class Topology(object):
    def __init__(self, domain='', controller=None, servers=None, drivers=None):
        if domain != '' and not domain.startswith('.'):
            domain = '.' + domain
        self.domain = domain
        self.controller = controller
        self.controllers = [controller]
        self.drivers = drivers
        self.servers = servers


# Mininet topology
mininet = Topology(
    controller=('mininet', '10.0.0.1', '00:00:0a:00:00:01'),
    servers=[
        ('mininet', '10.0.0.2', '00:00:0a:00:00:02'),
        ('mininet', '10.0.0.3', '00:00:0a:00:00:03')],
    drivers=[
        ('mininet', '10.0.0.4', '00:00:0a:00:00:04')])

# Testbed topology
testbed = Topology(
    domain='inf.ed.ac.uk',
    controller=('nsl002', '10.1.0.2', 'd4:ae:52:d1:f3:a0'),
    servers=[
        ('nsl003', '10.1.1.2', '9a:b0:ad:56:d9:34'),
        ('nsl004', '10.1.1.3', 'ee:3d:17:22:dc:2d'),
        ('nsl005', '10.1.2.2', 'd6:a7:d3:02:9c:bd'),
        ('nsl006', '10.1.2.3', 'fa:68:47:42:43:a1'),
        ('nsl007', '10.2.1.2', '82:91:b7:5a:63:28'),
        ('nsl008', '10.2.1.3', '2e:84:54:c3:76:d1'),
        ('nsl009', '10.2.2.2', '36:d5:29:59:63:2b'),
        ('nsl010', '10.2.2.3', 'be:6c:7d:62:31:31'),
        ('nsl011', '10.3.1.2', '1e:ca:c3:13:44:43'),
        ('nsl012', '10.3.1.3', 'f6:a6:11:17:44:36'),
        ('nsl013', '10.3.2.2', '06:70:d7:82:29:d0'),
        ('nsl014', '10.3.2.3', 'ba:13:cf:89:66:18'),
        ('nsl015', '10.4.1.2', '6e:47:a6:68:df:fb'),
        ('nsl016', '10.4.1.3', '3e:ff:e6:a4:1e:1f'),
        ('nsl017', '10.4.2.2', 'f6:2c:99:73:fc:d6'),
        ('nsl018', '10.4.2.3', '4e:b3:0c:da:61:23')],
    drivers=[
        ('nsl200', '10.5.1.2', '5c:b9:01:7b:45:50'),
        ('nsl201', '10.5.1.3', '50:65:f3:e6:cf:d4'),
        ('nsl202', '10.5.1.4', '5c:b9:01:7b:35:a0'),
        ('nsl203', '10.5.1.5', '50:65:f3:e6:bf:14'),
        ('nsl204', '10.5.1.6', '5c:b9:01:7b:d1:38'),
        ('nsl205', '10.5.1.7', '50:65:f3:e6:bf:24'),
        ('nsl206', '10.5.1.8', '5c:b9:01:7b:27:28'),
        ('nsl207', '10.5.1.9', '50:65:f3:e6:bf:38'),
        ('nsl208', '10.5.1.10', '5c:b9:01:7b:27:74'),
        ('nsl209', '10.5.1.11', '50:65:f3:e6:9f:a8')])
